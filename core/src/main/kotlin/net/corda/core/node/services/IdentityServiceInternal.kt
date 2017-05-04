package net.corda.core.node.services

import net.corda.core.crypto.AnonymousParty
import java.security.cert.CertPath
import java.security.cert.X509Certificate

/**
 * Functions for the identity service which are intended only for internal node usage.
 */
interface IdentityServiceInternal : IdentityService {
    /**
     * Verify and then store the certificates proving that an anonymous party's key is owned by the given full
     * party.
     *
     * @param trustedRoot root certificate for the identity.
     * @param anonymousParty an anonymised party belonging to the legal entity.
     * @param path certificate path from the trusted root to the anonymised party.
     * @throws IllegalArgumentException if the chain does not link the two parties, or if there is already an existing
     * certificate chain for the anonymous party. Anonymous parties must always resolve to a single owning party.
     */
    @Throws(IllegalArgumentException::class)
    fun registerPath(trustedRoot: X509Certificate, anonymousParty: AnonymousParty, path: CertPath)

}