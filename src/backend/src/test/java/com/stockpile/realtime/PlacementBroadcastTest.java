package com.stockpile.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.stockpile.inventory.domain.AccessFace;
import com.stockpile.inventory.domain.HandlingType;
import com.stockpile.inventory.domain.Location;
import com.stockpile.inventory.domain.Lot;
import com.stockpile.inventory.domain.Movement;
import com.stockpile.inventory.domain.MovementType;
import com.stockpile.inventory.domain.Sku;
import com.stockpile.inventory.domain.Warehouse;
import com.stockpile.inventory.repository.LocationRepository;
import com.stockpile.inventory.repository.LotRepository;
import com.stockpile.inventory.repository.SkuRepository;
import com.stockpile.inventory.repository.WarehouseRepository;
import com.stockpile.inventory.service.MovementService;
import com.stockpile.realtime.dto.PlacementDelta;

/**
 * End-to-end realtime test: a real STOMP client connects over a live port,
 * subscribes to a lane topic, and asserts that recording a movement pushes the
 * expected delta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PlacementBroadcastTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	@LocalServerPort int port;

	@Autowired MovementService movementService;
	@Autowired SkuRepository skuRepository;
	@Autowired LotRepository lotRepository;
	@Autowired LocationRepository locationRepository;
	@Autowired WarehouseRepository warehouseRepository;

	private Warehouse wh;

	@Test
	void putawayBroadcastsUpsertToTheLane() throws Exception {
		Location bin = bin("lane-A", 1, 2, 3);
		Lot lot = lot();
		BlockingQueue<PlacementDelta> deltas = subscribe("lane-A");

		movementService.record(putaway(lot, bin));

		PlacementDelta d = deltas.poll(5, TimeUnit.SECONDS);
		assertThat(d).isNotNull();
		assertThat(d.kind()).isEqualTo(PlacementDelta.ChangeKind.UPSERT);
		assertThat(d.lotId()).isEqualTo(lot.getId());
		assertThat(d.binId()).isEqualTo(bin.getId());
	}

	@Test
	void pickBroadcastsRemoveToTheLane() throws Exception {
		Location bin = bin("lane-B", 0, 0, 0);
		Lot lot = lot();
		movementService.record(putaway(lot, bin)); // place it first

		BlockingQueue<PlacementDelta> deltas = subscribe("lane-B");
		movementService.record(move(lot, bin, null, MovementType.PICK));

		PlacementDelta d = deltas.poll(5, TimeUnit.SECONDS);
		assertThat(d).isNotNull();
		assertThat(d.kind()).isEqualTo(PlacementDelta.ChangeKind.REMOVE);
		assertThat(d.lotId()).isEqualTo(lot.getId());
	}

	@Test
	void relocateAcrossLanesUpsertsDestinationAndRemovesOrigin() throws Exception {
		Location from = bin("lane-src", 0, 0, 0);
		Location to = bin("lane-dst", 5, 0, 0);
		Lot lot = lot();
		movementService.record(putaway(lot, from));

		BlockingQueue<PlacementDelta> src = subscribe("lane-src");
		BlockingQueue<PlacementDelta> dst = subscribe("lane-dst");
		movementService.record(move(lot, from, to, MovementType.RELOCATE));

		PlacementDelta atDst = dst.poll(5, TimeUnit.SECONDS);
		PlacementDelta atSrc = src.poll(5, TimeUnit.SECONDS);
		assertThat(atDst).isNotNull();
		assertThat(atDst.kind()).isEqualTo(PlacementDelta.ChangeKind.UPSERT);
		assertThat(atDst.binId()).isEqualTo(to.getId());
		assertThat(atSrc).isNotNull();
		assertThat(atSrc.kind()).isEqualTo(PlacementDelta.ChangeKind.REMOVE);
	}

	// --- STOMP client ---

	/** Subscribes to the warehouse-qualified lane topic (ADR-0009). */
	private BlockingQueue<PlacementDelta> subscribe(String lane) throws Exception {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		// The server serializes the Instant ts as an ISO string; the client converter
		// needs the JavaTimeModule to deserialize it back — otherwise the frame handler
		// throws and silently drops the message (nothing reaches the queue).
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.getObjectMapper().registerModule(new JavaTimeModule());
		client.setMessageConverter(converter);
		StompSession session = client
				.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
				.get(5, TimeUnit.SECONDS);

		BlockingQueue<PlacementDelta> queue = new LinkedBlockingQueue<>();
		session.subscribe("/topic/warehouse/" + warehouse().getId() + "/lane/" + lane,
				new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return PlacementDelta.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				queue.add((PlacementDelta) payload);
			}
		});
		return queue;
	}

	// --- data helpers ---

	/** One warehouse per test instance (bin codes stay unique via nanoTime). */
	private Warehouse warehouse() {
		if (wh == null) {
			Warehouse w = new Warehouse();
			w.setCode("WH-" + System.nanoTime());
			w.setName("Test warehouse");
			wh = warehouseRepository.save(w);
		}
		return wh;
	}

	private Sku sku() {
		Sku s = new Sku();
		s.setCode("S-" + System.nanoTime());
		s.setName("t");
		s.setW(BigDecimal.ONE);
		s.setD(BigDecimal.ONE);
		s.setH(BigDecimal.ONE);
		s.setWeight(BigDecimal.ONE);
		s.setHandling(HandlingType.FIFO);
		return skuRepository.save(s);
	}

	private Lot lot() {
		Lot l = new Lot();
		l.setSku(sku());
		l.setW(BigDecimal.ONE);
		l.setD(BigDecimal.ONE);
		l.setH(BigDecimal.ONE);
		l.setWeight(BigDecimal.ONE);
		return lotRepository.save(l);
	}

	private Location bin(String lane, double x, double y, double z) {
		Location l = new Location();
		l.setWarehouse(warehouse());
		l.setZone("Z");
		l.setAisle("A");
		l.setRack("R");
		l.setLevel("1");
		l.setBin("B-" + System.nanoTime());
		l.setX(BigDecimal.valueOf(x));
		l.setY(BigDecimal.valueOf(y));
		l.setZ(BigDecimal.valueOf(z));
		l.setW(BigDecimal.ONE);
		l.setD(BigDecimal.ONE);
		l.setH(BigDecimal.ONE);
		l.setLaneId(lane);
		l.setAccessFace(AccessFace.TOP);
		return locationRepository.save(l);
	}

	private Movement putaway(Lot lot, Location bin) {
		return move(lot, null, bin, MovementType.PUTAWAY);
	}

	private Movement move(Lot lot, Location from, Location to, MovementType type) {
		Movement m = new Movement();
		m.setLot(lot);
		m.setType(type);
		m.setFromBin(from);
		m.setToBin(to);
		return m;
	}
}
