package com.stockpile.transfer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stockpile.transfer.domain.Transfer;
import com.stockpile.transfer.domain.TransferStatus;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

	/** Transfers arriving at one warehouse in a given state (e.g. IN_TRANSIT). */
	List<Transfer> findByToWarehouseIdAndStatus(Long toWarehouseId, TransferStatus status);

	/** True while the lot is already mid-transfer — blocks opening a second one. */
	boolean existsByLotIdAndStatus(Long lotId, TransferStatus status);
}
