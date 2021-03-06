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

package io.bitsquare.trade.protocol.trade.messages;

import io.bitsquare.app.Version;
import io.bitsquare.btc.data.RawInput;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.messaging.MailboxMessage;
import io.bitsquare.payment.PaymentAccountContractData;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;
import java.util.List;

@Immutable
public final class PayDepositRequest extends TradeMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final long tradeAmount;
    public final byte[] takerTradeWalletPubKey;
    public final List<RawInput> rawInputs;
    public final long changeOutputValue;
    public final String changeOutputAddress;
    public final String takerPayoutAddressString;
    public final PubKeyRing takerPubKeyRing;
    public final PaymentAccountContractData takerPaymentAccountContractData;
    public final String takerAccountId;
    public final String takeOfferFeeTxId;
    public final List<Address> acceptedArbitratorAddresses;
    public final Address arbitratorAddress;
    private final Address senderAddress;

    public PayDepositRequest(Address senderAddress,
                             String tradeId,
                             long tradeAmount,
                             List<RawInput> rawInputs,
                             long changeOutputValue,
                             String changeOutputAddress,
                             byte[] takerTradeWalletPubKey,
                             String takerPayoutAddressString,
                             PubKeyRing takerPubKeyRing,
                             PaymentAccountContractData takerPaymentAccountContractData,
                             String takerAccountId,
                             String takeOfferFeeTxId,
                             List<Address> acceptedArbitratorAddresses,
                             Address arbitratorAddress) {
        super(tradeId);
        this.senderAddress = senderAddress;
        this.tradeAmount = tradeAmount;
        this.rawInputs = rawInputs;
        this.changeOutputValue = changeOutputValue;
        this.changeOutputAddress = changeOutputAddress;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.takerPubKeyRing = takerPubKeyRing;
        this.takerTradeWalletPubKey = takerTradeWalletPubKey;
        this.takerPaymentAccountContractData = takerPaymentAccountContractData;
        this.takerAccountId = takerAccountId;
        this.takeOfferFeeTxId = takeOfferFeeTxId;
        this.acceptedArbitratorAddresses = acceptedArbitratorAddresses;
        this.arbitratorAddress = arbitratorAddress;
    }

    @Override
    public Address getSenderAddress() {
        return senderAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PayDepositRequest)) return false;
        if (!super.equals(o)) return false;

        PayDepositRequest that = (PayDepositRequest) o;

        if (tradeAmount != that.tradeAmount) return false;
        if (changeOutputValue != that.changeOutputValue) return false;
        if (!Arrays.equals(takerTradeWalletPubKey, that.takerTradeWalletPubKey)) return false;
        if (rawInputs != null ? !rawInputs.equals(that.rawInputs) : that.rawInputs != null) return false;
        if (changeOutputAddress != null ? !changeOutputAddress.equals(that.changeOutputAddress) : that.changeOutputAddress != null)
            return false;
        if (takerPayoutAddressString != null ? !takerPayoutAddressString.equals(that.takerPayoutAddressString) : that.takerPayoutAddressString != null)
            return false;
        if (takerPubKeyRing != null ? !takerPubKeyRing.equals(that.takerPubKeyRing) : that.takerPubKeyRing != null)
            return false;
        if (takerPaymentAccountContractData != null ? !takerPaymentAccountContractData.equals(that.takerPaymentAccountContractData) : that.takerPaymentAccountContractData != null)
            return false;
        if (takerAccountId != null ? !takerAccountId.equals(that.takerAccountId) : that.takerAccountId != null)
            return false;
        if (takeOfferFeeTxId != null ? !takeOfferFeeTxId.equals(that.takeOfferFeeTxId) : that.takeOfferFeeTxId != null)
            return false;
        if (acceptedArbitratorAddresses != null ? !acceptedArbitratorAddresses.equals(that.acceptedArbitratorAddresses) : that.acceptedArbitratorAddresses != null)
            return false;
        if (arbitratorAddress != null ? !arbitratorAddress.equals(that.arbitratorAddress) : that.arbitratorAddress != null)
            return false;
        return !(senderAddress != null ? !senderAddress.equals(that.senderAddress) : that.senderAddress != null);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (tradeAmount ^ (tradeAmount >>> 32));
        result = 31 * result + (takerTradeWalletPubKey != null ? Arrays.hashCode(takerTradeWalletPubKey) : 0);
        result = 31 * result + (rawInputs != null ? rawInputs.hashCode() : 0);
        result = 31 * result + (int) (changeOutputValue ^ (changeOutputValue >>> 32));
        result = 31 * result + (changeOutputAddress != null ? changeOutputAddress.hashCode() : 0);
        result = 31 * result + (takerPayoutAddressString != null ? takerPayoutAddressString.hashCode() : 0);
        result = 31 * result + (takerPubKeyRing != null ? takerPubKeyRing.hashCode() : 0);
        result = 31 * result + (takerPaymentAccountContractData != null ? takerPaymentAccountContractData.hashCode() : 0);
        result = 31 * result + (takerAccountId != null ? takerAccountId.hashCode() : 0);
        result = 31 * result + (takeOfferFeeTxId != null ? takeOfferFeeTxId.hashCode() : 0);
        result = 31 * result + (acceptedArbitratorAddresses != null ? acceptedArbitratorAddresses.hashCode() : 0);
        result = 31 * result + (arbitratorAddress != null ? arbitratorAddress.hashCode() : 0);
        result = 31 * result + (senderAddress != null ? senderAddress.hashCode() : 0);
        return result;
    }
}
