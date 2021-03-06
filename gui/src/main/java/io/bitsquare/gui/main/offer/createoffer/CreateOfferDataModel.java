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

package io.bitsquare.gui.main.offer.createoffer;

import com.google.inject.Inject;
import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.gui.common.model.ActivatableDataModel;
import io.bitsquare.gui.popups.WalletPasswordPopup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.SepaAccount;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Domain for that UI element.
 * Note that the create offer domain has a deeper scope in the application domain (TradeManager).
 * That model is just responsible for the domain specific parts displayed needed in that UI element.
 */
class CreateOfferDataModel extends ActivatableDataModel {
    private final OpenOfferManager openOfferManager;
    private final WalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final Preferences preferences;
    private final User user;
    private final KeyRing keyRing;
    private P2PService p2PService;
    private final WalletPasswordPopup walletPasswordPopup;
    private final BSFormatter formatter;
    private final String offerId;
    private final AddressEntry addressEntry;
    private final Coin offerFeeAsCoin;
    private final Coin networkFeeAsCoin;
    private final Coin securityDepositAsCoin;
    private final BalanceListener balanceListener;
    private final SetChangeListener<PaymentAccount> paymentAccountsChangeListener;

    private Offer.Direction direction;

    private TradeCurrency tradeCurrency;

    final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();

    final BooleanProperty isWalletFunded = new SimpleBooleanProperty();
    final BooleanProperty useMBTC = new SimpleBooleanProperty();

    final ObjectProperty<Coin> amountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> minAmountAsCoin = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> priceAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Fiat> volumeAsFiat = new SimpleObjectProperty<>();
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();

    final ObservableList<PaymentAccount> paymentAccounts = FXCollections.observableArrayList();

    private PaymentAccount paymentAccount;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    CreateOfferDataModel(OpenOfferManager openOfferManager, WalletService walletService, TradeWalletService tradeWalletService,
                         Preferences preferences, User user, KeyRing keyRing, P2PService p2PService,
                         WalletPasswordPopup walletPasswordPopup, BSFormatter formatter) {
        this.openOfferManager = openOfferManager;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.preferences = preferences;
        this.user = user;
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.walletPasswordPopup = walletPasswordPopup;
        this.formatter = formatter;

        offerId = UUID.randomUUID().toString();
        addressEntry = walletService.getAddressEntryByOfferId(offerId);
        offerFeeAsCoin = FeePolicy.CREATE_OFFER_FEE;
        networkFeeAsCoin = FeePolicy.TX_FEE;
        securityDepositAsCoin = FeePolicy.SECURITY_DEPOSIT;

        balanceListener = new BalanceListener(getAddressEntry().getAddress()) {
            @Override
            public void onBalanceChanged(@NotNull Coin balance) {
                updateBalance(balance);
            }
        };

        paymentAccountsChangeListener = change -> paymentAccounts.setAll(user.getPaymentAccounts());
    }


    @Override
    protected void activate() {
        addBindings();
        addListeners();

        paymentAccounts.setAll(user.getPaymentAccounts());
        updateBalance(walletService.getBalanceForAddress(getAddressEntry().getAddress()));

        if (direction == Offer.Direction.BUY)
            calculateTotalToPay();
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
    }

    private void addBindings() {
        btcCode.bind(preferences.btcDenominationProperty());
    }

    private void removeBindings() {
        btcCode.unbind();
    }

    private void addListeners() {
        walletService.addBalanceListener(balanceListener);
        user.getPaymentAccountsAsObservable().addListener(paymentAccountsChangeListener);
    }

    private void removeListeners() {
        walletService.removeBalanceListener(balanceListener);
        user.getPaymentAccountsAsObservable().removeListener(paymentAccountsChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void initWithData(Offer.Direction direction, TradeCurrency tradeCurrency) {
        this.direction = direction;
        this.tradeCurrency = tradeCurrency;

        tradeCurrencyCode.set(tradeCurrency.getCode());
        PaymentAccount account = user.findFirstPaymentAccountWithCurrency(tradeCurrency);
        if (account != null)
            paymentAccount = account;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    Offer getOffer() {
        long fiatPrice = priceAsFiat.get() != null ? priceAsFiat.get().getValue() : 0L;
        long amount = amountAsCoin.get() != null ? amountAsCoin.get().getValue() : 0L;
        long minAmount = minAmountAsCoin.get() != null ? minAmountAsCoin.get().getValue() : 0L;

        List<String> acceptedCountryCodes = null;
        if (paymentAccount instanceof SepaAccount)
            acceptedCountryCodes = ((SepaAccount) paymentAccount).getAcceptedCountryCodes();

        // That is optional and set to null if not supported (AltCoins, OKPay,...)
        Country country = paymentAccount.getCountry();

        checkNotNull(p2PService.getAddress(), "Address must not be null");
        return new Offer(offerId,
                p2PService.getAddress(),
                keyRing.getPubKeyRing(),
                direction,
                fiatPrice,
                amount,
                minAmount,
                paymentAccount.getPaymentMethod().getId(),
                tradeCurrencyCode.get(),
                country,
                paymentAccount.getId(),
                user.getAcceptedArbitratorAddresses(),
                acceptedCountryCodes);
    }

    void onPlaceOffer(Offer offer, TransactionResultHandler resultHandler) {
        if (walletService.getWallet().isEncrypted() && tradeWalletService.getAesKey() == null) {
            walletPasswordPopup.show().onAesKey(aesKey -> {
                tradeWalletService.setAesKey(aesKey);
                doPlaceOffer(offer, resultHandler);
            });
        } else {
            doPlaceOffer(offer, resultHandler);
        }
    }

    private void doPlaceOffer(Offer offer, TransactionResultHandler resultHandler) {
        openOfferManager.onPlaceOffer(offer,
                resultHandler
        );
    }

    void onSecurityDepositInfoDisplayed() {
        preferences.setDisplaySecurityDepositInfo(false);
    }


    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        if (paymentAccount != null)
            this.paymentAccount = paymentAccount;
    }

    public void onCurrencySelected(TradeCurrency tradeCurrency) {
        if (tradeCurrency != null) {
            this.tradeCurrency = tradeCurrency;
            tradeCurrencyCode.set(tradeCurrency.getCode());

            paymentAccount.setSelectedTradeCurrency(tradeCurrency);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isMinAmountLessOrEqualAmount() {
        //noinspection SimplifiableIfStatement
        if (minAmountAsCoin.get() != null && amountAsCoin.get() != null)
            return !minAmountAsCoin.get().isGreaterThan(amountAsCoin.get());
        return true;
    }

    Offer.Direction getDirection() {
        return direction;
    }

    String getOfferId() {
        return offerId;
    }

    AddressEntry getAddressEntry() {
        return addressEntry;
    }

    boolean getDisplaySecurityDepositInfo() {
        return preferences.getDisplaySecurityDepositInfo();
    }

    public TradeCurrency getTradeCurrency() {
        return tradeCurrency;
    }

    public PaymentAccount getPaymentAccount() {
        return paymentAccount;
    }

    boolean hasAcceptedArbitrators() {
        return user.getAcceptedArbitrators().size() > 0;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    void calculateVolume() {
        if (priceAsFiat.get() != null &&
                amountAsCoin.get() != null &&
                !amountAsCoin.get().isZero() &&
                !priceAsFiat.get().isZero()) {
            volumeAsFiat.set(new ExchangeRate(priceAsFiat.get()).coinToFiat(amountAsCoin.get()));
        }
    }

    void calculateAmount() {
        if (volumeAsFiat.get() != null &&
                priceAsFiat.get() != null &&
                !volumeAsFiat.get().isZero() &&
                !priceAsFiat.get().isZero()) {
            // If we got a btc value with more then 4 decimals we convert it to max 4 decimals
            amountAsCoin.set(formatter.reduceTo4Decimals(new ExchangeRate(priceAsFiat.get()).fiatToCoin(volumeAsFiat.get())));

            calculateTotalToPay();
        }
    }

    void calculateTotalToPay() {
        if (securityDepositAsCoin != null) {
            if (direction == Offer.Direction.BUY)
                totalToPayAsCoin.set(offerFeeAsCoin.add(networkFeeAsCoin).add(securityDepositAsCoin));
            else
                totalToPayAsCoin.set(offerFeeAsCoin.add(networkFeeAsCoin).add(securityDepositAsCoin).add(amountAsCoin.get()));
        }
    }

    private void updateBalance(Coin balance) {
        isWalletFunded.set(totalToPayAsCoin.get() != null && balance.compareTo(totalToPayAsCoin.get()) >= 0);
    }

    public Coin getOfferFeeAsCoin() {
        return offerFeeAsCoin;
    }

    public Coin getNetworkFeeAsCoin() {
        return networkFeeAsCoin;
    }

    public Coin getSecurityDepositAsCoin() {
        return securityDepositAsCoin;
    }

    public List<Arbitrator> getArbitrators() {
        return user.getAcceptedArbitrators();
    }

    public void setShowPlaceOfferConfirmation(boolean selected) {
        preferences.setShowPlaceOfferConfirmation(selected);
    }

    public boolean getShowPlaceOfferConfirmation() {
        return preferences.getShowPlaceOfferConfirmation();
    }

    public Preferences getPreferences() {
        return preferences;
    }
}
