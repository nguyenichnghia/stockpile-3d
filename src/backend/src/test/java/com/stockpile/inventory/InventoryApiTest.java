package com.stockpile.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
		long skuId = createSku("SKU-FLOW");
		long binId = createLocation("FLOW-BIN");
		long lotId = createLot(skuId);

		// record a PUTAWAY into the bin
		String movement = """
				{"lotId":%d,"type":"PUTAWAY","toBin":%d}""".formatted(lotId, binId);
		mvc.perform(post("/api/movements").contentType(MediaType.APPLICATION_JSON).content(movement))
				.andExpect(status().isCreated());

		// placement projection now shows the lot in that bin
		mvc.perform(get("/api/placements"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.lotId==" + lotId + " && @.binId==" + binId + ")]").exists());
	}

	@Test
	void getMissingSkuReturns404() throws Exception {
		mvc.perform(get("/api/skus/999999")).andExpect(status().isNotFound());
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

	private long createSku(String code) throws Exception {
		String body = """
				{"code":"%s","name":"test","w":1,"d":1,"h":1,"weight":1,"handling":"FIFO"}"""
				.formatted(code);
		String res = mvc.perform(post("/api/skus").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return id(res);
	}

	private long createLocation(String bin) throws Exception {
		String body = """
				{"zone":"Z","aisle":"A","rack":"R","level":"1","bin":"%s",\
				"x":0,"y":0,"z":0,"w":1,"d":1,"h":1,"laneId":"lane-1","accessFace":"NORTH"}"""
				.formatted(bin);
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
