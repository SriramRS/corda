/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.testing

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import contracts.CommercialPaper
import core.*
import core.node.services.NodeWalletService
import core.utilities.BriefLogFormatter
import protocols.TwoPartyTradeProtocol
import java.time.Instant

/**
 * Simulates a never ending series of trades that go pair-wise through the banks (e.g. A and B trade with each other,
 * then B and C trade with each other, then C and A etc).
 */
class TradeSimulation(runAsync: Boolean, latencyInjector: InMemoryMessagingNetwork.LatencyCalculator?) : Simulation(runAsync, latencyInjector) {
    override fun start() {
        BriefLogFormatter.loggingOn("bank", "core.TransactionGroup", "recordingmap")
        startTradingCircle { i, j -> tradeBetween(i, j) }
    }

    private fun tradeBetween(buyerBankIndex: Int, sellerBankIndex: Int): ListenableFuture<MutableList<SignedTransaction>> {
        val buyer = banks[buyerBankIndex]
        val seller = banks[sellerBankIndex]

        (buyer.services.walletService as NodeWalletService).fillWithSomeTestCash(1500.DOLLARS)

        val issuance = run {
            val tx = CommercialPaper().generateIssue(seller.info.identity.ref(1, 2, 3), 1100.DOLLARS, Instant.now() + 10.days)
            tx.setTime(Instant.now(), timestamper.info.identity, 30.seconds)
            tx.signWith(timestamper.storage.myLegalIdentityKey)
            tx.signWith(seller.storage.myLegalIdentityKey)
            tx.toSignedTransaction(true)
        }
        seller.services.storageService.validatedTransactions[issuance.id] = issuance

        val sessionID = random63BitValue()
        val buyerProtocol = TwoPartyTradeProtocol.Buyer(seller.net.myAddress, timestamper.info.identity,
                1000.DOLLARS, CommercialPaper.State::class.java, sessionID)
        val sellerProtocol = TwoPartyTradeProtocol.Seller(buyer.net.myAddress, timestamper.info,
                issuance.tx.outRef(0), 1000.DOLLARS, seller.storage.myLegalIdentityKey, sessionID)

        linkConsensus(listOf(buyer, seller, timestamper), sellerProtocol)
        linkProtocolProgress(buyer, buyerProtocol)
        linkProtocolProgress(seller, sellerProtocol)

        val buyerFuture = buyer.smm.add("bank.$buyerBankIndex.${TwoPartyTradeProtocol.TRADE_TOPIC}.buyer", buyerProtocol)
        val sellerFuture = seller.smm.add("bank.$sellerBankIndex.${TwoPartyTradeProtocol.TRADE_TOPIC}.seller", sellerProtocol)

        return Futures.successfulAsList(buyerFuture, sellerFuture)
    }
}