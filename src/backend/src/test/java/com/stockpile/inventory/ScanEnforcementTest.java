package com.stockpile.inventory;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Server-side scan enforcement (the slice ADR-0007 left room for): a warehouse
 * with {@code requireScan} on rejects movements whose scanRef is missing or is
 * not the movement's lot barcode; warehouses with the flag off keep the v1
 * encourage-and-audit contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ScanEnforcementTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired MockMvc mvc;
	private final ObjectMapper json = new ObjectMapper();

	@Test
	void requireScanDefaultsOffAndIsTogglableViaPatch() throws Exception {
		long warehouseId = createWarehouse(false);

		mvc.perform(get("/api/warehouses/" + warehouseId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requireScan").value(false));

		mvc.perform(patch("/api/warehouses/" + warehouseId)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"requireScan":true}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requireScan").value(true));

		// An empty patch changes nothing.
		mvc.perform(patch("/api/warehouses/" + warehouseId)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requireScan").value(true));
	}

	@Test
	void enforcedWarehouseRejectsMovementWithoutScanRef() throws Exception {
		Fixture f = fixture(true);

		String movement = """
				{"lotId":%d,"type":"PUTAWAY","toBin":%d}""".formatted(f.lotId, f.binId);
		mvc.perform(post("/api/movements").contentType(MediaType.APPLICATION_JSON).content(movement))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("scanRef is required")));
	}

	@Test
	void enforcedWarehouseRejectsScanRefOfAnotherLot() throws Exception {
		Fixture f = fixture(true);

		String movement = """
				{"lotId":%d,"type":"PUTAWAY","toBin":%d,"scanRef":"LOT-%d"}"""
				.formatted(f.lotId, f.binId, f.lotId + 1);
		mvc.perform(post("/api/movements").contentType(MediaType.APPLICATION_JSON).content(movement))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("does not match")));
	}

	@Test
	void enforcedWarehouseAcceptsMatchingScanRefCaseInsensitive() throws Exception {
		Fixture f = fixture(true);

		// Lowercase on purpose: the resolver's LOT pattern is case-insensitive
		// (ADR-0007), so enforcement must be too. The raw string is kept.
		String movement = """
				{"lotId":%d,"type":"PUTAWAY","toBin":%d,"scanRef":"lot-%d"}"""
				.formatted(f.lotId, f.binId, f.lotId);
		mvc.perform(post("/api/movements").contentType(MediaType.APPLICATION_JSON).content(movement))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.scanRef").value("lot-" + f.lotId));
	}

	@Test
	void enforcementCoversBinlessInboundToStaging() throws Exception {
		// No bins: the warehouse comes from the stated warehouseId, and the
		// policy must still apply (every physical touchpoint, docs/01 §8.6).
		Fixture f = fixture(true);

		String inbound = """
				{"lotId":%d,"type":"INBOUND","warehouseId":%d}""".formatted(f.lotId, f.warehouseId);
		mvc.perform(post("/api/movements").contentType(MediaType.APPLICATION_JSON).content(inbound))
				.andExpect(status().isBadRequest());

		String scanned = """
				{"lotId":%d,"type":"INBOUND","warehouseId":%d,"scanRef":"LOT-%d"}"""
				.formatted(f.lotId, f.warehouseId, f.lotId);
		mvc.perform(post("/api/movements").contentType(MediaType.APPLICATION_JSON).content(scanned))
				.andExpect(status().isCreated());
	}

	@Test
	void unenforcedWarehouseStillRecordsAnyScanRef() throws Exception {
		// The v1 contract (ADR-0007) is unchanged while the flag is off: no
		// scanRef, or one that matches nothing, is recorded as-is.
		Fixture f = fixture(false);

		String movement = """
				{"lotId":%d,"type":"PUTAWAY","toBin":%d,"scanRef":"free-text"}"""
				.formatted(f.lotId, f.binId);
		mvc.perform(post("/api/movements").contentType(MediaType.APPLICATION_JSON).content(movement))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.scanRef").value("free-text"));
	}

	// --- helpers ---

	private record Fixture(long warehouseId, long binId, long lotId) {
	}

	private Fixture fixture(boolean requireScan) throws Exception {
		long warehouseId = createWarehouse(requireScan);
		long skuId = createSku("SKU-SCAN-" + System.nanoTime());
		long binId = createLocation(warehouseId);
		long lotId = createLot(skuId);
		return new Fixture(warehouseId, binId, lotId);
	}

	private long createWarehouse(boolean requireScan) throws Exception {
		String body = """
				{"code":"WH-%s","name":"Scan test warehouse","requireScan":%b}"""
				.formatted(System.nanoTime(), requireScan);
		String res = mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.requireScan").value(requireScan))
				.andReturn().getResponse().getContentAsString();
		return id(res);
	}

	private long createSku(String code) throws Exception {
		String body = """
				{"code":"%s","name":"test","w":1,"d":1,"h":1,"weight":1,"handling":"FIFO"}"""
				.formatted(code);
		String res = mvc.perform(post("/api/skus").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return id(res);
	}

	private long createLocation(long warehouseId) throws Exception {
		String body = """
				{"warehouseId":%d,"zone":"Z","aisle":"A","rack":"R","level":"1","bin":"SCAN",\
				"x":0,"y":0,"z":0,"w":1,"d":1,"h":1,"laneId":"lane-1","accessFace":"NORTH"}"""
				.formatted(warehouseId);
		String res = mvc.perform(post("/api/locations").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return id(res);
	}

	private long createLot(long skuId) throws Exception {
		String body = """
				{"skuId":%d,"w":1,"d":1,"h":1,"weight":1}""".formatted(skuId);
		String res = mvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return id(res);
	}

	private long id(String responseJson) throws Exception {
		JsonNode node = json.readTree(responseJson);
		return node.get("id").asLong();
	}
}
