package com.stockpile.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InventoryApiTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@Autowired MockMvc mvc;
	private final ObjectMapper json = new ObjectMapper();

	@Test
	void fullFlowPutawayThenPlacementVisible() throws Exception {
		long warehouseId = createWarehouse();
		long skuId = createSku("SKU-FLOW");
		long binId = createLocation(warehouseId, "FLOW-BIN");
		long lotId = createLot(skuId);

		// record a PUTAWAY into the bin
		String movement = """
				{"lotId":%d,"type":"PUTAWAY","toBin":%d}""".formatted(lotId, binId);
		mvc.perform(post("/api/movements").contentType(MediaType.APPLICATION_JSON).content(movement))
				.andExpect(status().isCreated());

		// placement projection now shows the lot in that bin
		mvc.perform(get("/api/placements").param("warehouseId", String.valueOf(warehouseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.lotId==" + lotId + " && @.binId==" + binId + ")]").exists());
	}

	@Test
	void warehousesCanBeCreatedAndListed() throws Exception {
		long id = createWarehouse();

		mvc.perform(get("/api/warehouses"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id==" + id + ")]").exists());
		mvc.perform(get("/api/warehouses/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Test warehouse"));
	}

	@Test
	void warehouseTimezoneDefaultsToUtcAndIsPatchable() throws Exception {
		long id = createWarehouse();

		mvc.perform(get("/api/warehouses/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("UTC"));

		mvc.perform(patch("/api/warehouses/" + id)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"timezone":"Asia/Ho_Chi_Minh"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"));

		mvc.perform(patch("/api/warehouses/" + id)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"timezone":"Mars/Olympus"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						org.hamcrest.Matchers.containsString("Unknown timezone")));
	}

	@Test
	void duplicateWarehouseCodeReturns400() throws Exception {
		String body = """
				{"code":"WH-DUP","name":"Test warehouse"}""";
		mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getMissingSkuReturns404() throws Exception {
		mvc.perform(get("/api/skus/999999")).andExpect(status().isNotFound());
	}

	@Test
	void missingRequiredParamReturns400WithMessage() throws Exception {
		// The frontend surfaces {message}; Spring's default body has none.
		mvc.perform(get("/api/placements"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						org.hamcrest.Matchers.containsString("warehouseId")));
	}

	@Test
	void nonNumericParamReturns400WithMessage() throws Exception {
		mvc.perform(get("/api/placements").param("warehouseId", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						org.hamcrest.Matchers.containsString("warehouseId")));
	}

	@Test
	void createSkuWithBlankCodeReturns400() throws Exception {
		String bad = """
				{"code":"","name":"x","w":1,"d":1,"h":1,"weight":1,"handling":"FIFO"}""";
		mvc.perform(post("/api/skus").contentType(MediaType.APPLICATION_JSON).content(bad))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deleteSkuRemovesIt() throws Exception {
		long skuId = createSku("SKU-DEL");
		mvc.perform(delete("/api/skus/" + skuId)).andExpect(status().isNoContent());
		mvc.perform(get("/api/skus/" + skuId)).andExpect(status().isNotFound());
	}

	// --- helpers ---

	private long createWarehouse() throws Exception {
		String body = """
				{"code":"WH-%s","name":"Test warehouse"}""".formatted(System.nanoTime());
		String res = mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
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

	private long createLocation(long warehouseId, String bin) throws Exception {
		String body = """
				{"warehouseId":%d,"zone":"Z","aisle":"A","rack":"R","level":"1","bin":"%s",\
				"x":0,"y":0,"z":0,"w":1,"d":1,"h":1,"laneId":"lane-1","accessFace":"NORTH"}"""
				.formatted(warehouseId, bin);
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
