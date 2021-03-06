/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.arbitration;

import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.storage.data.PubKeyProtectedExpirablePayload;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public final class Arbitrator implements PubKeyProtectedExpirablePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public static final long TTL = 1 * 24 * 60 * 60 * 1000; // 1 day

    // Persisted fields
    private final byte[] btcPubKey;
    private final PubKeyRing pubKeyRing;
    private final Address arbitratorAddress;
    private final List<String> languageCodes;
    private final String btcAddress;
    private final long registrationDate;
    private final String registrationSignature;
    private final byte[] registrationPubKey;

    public Arbitrator(Address arbitratorAddress,
                      byte[] btcPubKey,
                      String btcAddress,
                      PubKeyRing pubKeyRing,
                      List<String> languageCodes,
                      Date registrationDate,
                      byte[] registrationPubKey,
                      String registrationSignature) {
        this.arbitratorAddress = arbitratorAddress;
        this.btcPubKey = btcPubKey;
        this.btcAddress = btcAddress;
        this.pubKeyRing = pubKeyRing;
        this.languageCodes = languageCodes;
        this.registrationDate = registrationDate.getTime();
        this.registrationPubKey = registrationPubKey;
        this.registrationSignature = registrationSignature;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }

    public byte[] getBtcPubKey() {
        return btcPubKey;
    }

    public PubKeyRing getPubKeyRing() {
        return pubKeyRing;
    }

    public Address getArbitratorAddress() {
        return arbitratorAddress;
    }

    public Date getRegistrationDate() {
        return new Date(registrationDate);
    }

    public String getBtcAddress() {
        return btcAddress;
    }

    public List<String> getLanguageCodes() {
        return languageCodes;
    }

    public String getRegistrationSignature() {
        return registrationSignature;
    }

    public byte[] getRegistrationPubKey() {
        return registrationPubKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Arbitrator)) return false;

        Arbitrator that = (Arbitrator) o;

        if (registrationDate != that.registrationDate) return false;
        if (!Arrays.equals(btcPubKey, that.btcPubKey)) return false;
        if (pubKeyRing != null ? !pubKeyRing.equals(that.pubKeyRing) : that.pubKeyRing != null) return false;
        if (arbitratorAddress != null ? !arbitratorAddress.equals(that.arbitratorAddress) : that.arbitratorAddress != null)
            return false;
        if (languageCodes != null ? !languageCodes.equals(that.languageCodes) : that.languageCodes != null)
            return false;
        if (btcAddress != null ? !btcAddress.equals(that.btcAddress) : that.btcAddress != null) return false;
        if (registrationSignature != null ? !registrationSignature.equals(that.registrationSignature) : that.registrationSignature != null)
            return false;
        return Arrays.equals(registrationPubKey, that.registrationPubKey);

    }

    @Override
    public int hashCode() {
        int result = btcPubKey != null ? Arrays.hashCode(btcPubKey) : 0;
        result = 31 * result + (pubKeyRing != null ? pubKeyRing.hashCode() : 0);
        result = 31 * result + (arbitratorAddress != null ? arbitratorAddress.hashCode() : 0);
        result = 31 * result + (languageCodes != null ? languageCodes.hashCode() : 0);
        result = 31 * result + (btcAddress != null ? btcAddress.hashCode() : 0);
        result = 31 * result + (int) (registrationDate ^ (registrationDate >>> 32));
        result = 31 * result + (registrationSignature != null ? registrationSignature.hashCode() : 0);
        result = 31 * result + (registrationPubKey != null ? Arrays.hashCode(registrationPubKey) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Arbitrator{" +
                "arbitratorAddress=" + arbitratorAddress +
                ", languageCodes=" + languageCodes +
                ", btcAddress='" + btcAddress + '\'' +
                ", registrationDate=" + registrationDate +
                ", btcPubKey.hashCode()=" + Arrays.toString(btcPubKey).hashCode() +
                ", pubKeyRing.hashCode()=" + pubKeyRing.hashCode() +
                ", registrationSignature.hashCode()='" + registrationSignature.hashCode() + '\'' +
                ", registrationPubKey.hashCode()=" + Arrays.toString(registrationPubKey).hashCode() +
                '}';
    }
}
