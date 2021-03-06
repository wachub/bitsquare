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

package io.bitsquare.gui.main;

import io.bitsquare.BitsquareException;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.*;
import io.bitsquare.gui.components.SystemNotification;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.disputes.DisputesView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.market.MarketView;
import io.bitsquare.gui.main.offer.BuyOfferView;
import io.bitsquare.gui.main.offer.SellOfferView;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.settings.SettingsView;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.util.Transitions;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

import static javafx.scene.layout.AnchorPane.*;

@FxmlView
public class MainView extends InitializableView<StackPane, MainViewModel> {

    public static final String TITLE_KEY = "view.title";

    public static BorderPane getBaseApplicationContainer() {
        return baseApplicationContainer;
    }

    public static void blur() {
        transitions.blur(baseApplicationContainer);
    }

    public static void blurLight() {
        transitions.blur(baseApplicationContainer, Transitions.DEFAULT_DURATION, true, false, 5);
    }

    public static void removeBlur() {
        transitions.removeBlur(baseApplicationContainer);
    }

    private final ToggleGroup navButtons = new ToggleGroup();

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private static Transitions transitions;
    private final String title;
    private ChangeListener<String> walletServiceErrorMsgListener;
    private ChangeListener<String> blockchainSyncIconIdListener;
    private ChangeListener<String> splashP2PNetworkErrorMsgListener;
    private ChangeListener<String> splashP2PNetworkIconIdListener;
    private ChangeListener<Number> splashP2PNetworkProgressListener;
    private ProgressIndicator splashP2PNetworkIndicator;
    private Label splashP2PNetworkLabel;
    private ProgressBar blockchainSyncIndicator;
    private Label blockchainSyncLabel;
    private List<String> persistedFilesCorrupted;
    private static BorderPane baseApplicationContainer;

    @Inject
    public MainView(MainViewModel model, CachingViewLoader viewLoader, Navigation navigation, Transitions transitions,
                    @Named(MainView.TITLE_KEY) String title) {
        super(model);
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        MainView.transitions = transitions;
        this.title = title;
    }

    @Override
    protected void initialize() {
        ToggleButton marketButton = new NavButton(MarketView.class, "Market");
        ToggleButton buyButton = new NavButton(BuyOfferView.class, "Buy BTC");
        ToggleButton sellButton = new NavButton(SellOfferView.class, "Sell BTC");
        ToggleButton portfolioButton = new NavButton(PortfolioView.class, "Portfolio");
        ToggleButton fundsButton = new NavButton(FundsView.class, "Funds");
        ToggleButton disputesButton = new NavButton(DisputesView.class, "Support");
        ToggleButton settingsButton = new NavButton(SettingsView.class, "Settings");
        ToggleButton accountButton = new NavButton(AccountView.class, "Account");
        Pane portfolioButtonHolder = new Pane(portfolioButton);
        Pane disputesButtonHolder = new Pane(disputesButton);

        HBox leftNavPane = new HBox(marketButton, buyButton, sellButton, portfolioButtonHolder, fundsButton, disputesButtonHolder) {{
            setSpacing(10);
            setLeftAnchor(this, 10d);
            setTopAnchor(this, 0d);
        }};

        Tuple2<TextField, VBox> availableBalanceBox = getBalanceBox("Available balance");
        availableBalanceBox.first.textProperty().bind(model.availableBalance);

        Tuple2<TextField, VBox> lockedBalanceBox = getBalanceBox("Locked balance");
        lockedBalanceBox.first.textProperty().bind(model.lockedBalance);

        HBox rightNavPane = new HBox(availableBalanceBox.second, lockedBalanceBox.second, settingsButton, accountButton) {{
            setSpacing(10);
            setRightAnchor(this, 10d);
            setTopAnchor(this, 0d);
        }};

        AnchorPane contentContainer = new AnchorPane() {{
            setId("content-pane");
            setLeftAnchor(this, 0d);
            setRightAnchor(this, 0d);
            setTopAnchor(this, 60d);
            setBottomAnchor(this, 10d);
        }};

        AnchorPane applicationContainer = new AnchorPane(leftNavPane, rightNavPane, contentContainer) {{
            setId("content-pane");
        }};

        baseApplicationContainer = new BorderPane(applicationContainer) {{
            setId("base-content-container");
        }};
        baseApplicationContainer.setBottom(createFooter());

        setupNotificationIcon(portfolioButtonHolder);

        setupDisputesIcon(disputesButtonHolder);

        navigation.addListener(viewPath -> {
            if (viewPath.size() != 2 || viewPath.indexOf(MainView.class) != 0)
                return;

            Class<? extends View> viewClass = viewPath.tip();
            View view = viewLoader.load(viewClass);
            contentContainer.getChildren().setAll(view.getRoot());

            navButtons.getToggles().stream()
                    .filter(toggle -> toggle instanceof NavButton)
                    .filter(button -> viewClass == ((NavButton) button).viewClass)
                    .findFirst()
                    .orElseThrow(() -> new BitsquareException("No button matching %s found", viewClass))
                    .setSelected(true);
        });

        VBox splashScreen = createSplashScreen();

        root.getChildren().addAll(baseApplicationContainer, splashScreen);

        model.showAppScreen.addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                navigation.navigateToPreviousVisitedView();

                if (!persistedFilesCorrupted.isEmpty()) {
                    // show warning that some files has been corrupted
                    new Popup().warning("Those data base file(s) are not compatible with our current code base." +
                                    "\n" + persistedFilesCorrupted.toString() +
                                    "\n\nWe made a backup of the corrupted file(s) and applied the default values." +
                                    "\n\nThe backup is located at: [data directory]/db/corrupted"
                    ).show();
                }

                transitions.fadeOutAndRemove(splashScreen, 1500, actionEvent -> disposeSplashScreen());
            }
        });

        // Delay a bit to give time for rendering the splash screen
        UserThread.execute(model::initializeAllServices);
    }

    private Tuple2<TextField, VBox> getBalanceBox(String text) {
        TextField textField = new TextField();
        textField.setEditable(false);
        textField.setPrefWidth(100);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);
        textField.setStyle("-fx-alignment: center;  -fx-background-color: white;");

        Label label = new Label(text);
        label.setId("nav-balance-label");
        label.setPadding(new Insets(0, 5, 0, 5));
        label.setPrefWidth(textField.getPrefWidth());
        VBox vBox = new VBox();
        vBox.setSpacing(3);
        vBox.setPadding(new Insets(11, 0, 0, 0));
        vBox.getChildren().addAll(textField, label);
        return new Tuple2(textField, vBox);
    }

    public void setPersistedFilesCorrupted(List<String> persistedFilesCorrupted) {
        this.persistedFilesCorrupted = persistedFilesCorrupted;
    }

    private VBox createSplashScreen() {
        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(0);
        vBox.setId("splash");

        ImageView logo = new ImageView();
        logo.setId("image-splash-logo");


        // createBitcoinInfoBox
        blockchainSyncLabel = new Label();
        blockchainSyncLabel.textProperty().bind(model.blockchainSyncInfo);
        walletServiceErrorMsgListener = (ov, oldValue, newValue) -> {
            blockchainSyncLabel.setId("splash-error-state-msg");
        };
        model.walletServiceErrorMsg.addListener(walletServiceErrorMsgListener);

        blockchainSyncIndicator = new ProgressBar(-1);
        blockchainSyncIndicator.setPrefWidth(120);
        blockchainSyncIndicator.progressProperty().bind(model.blockchainSyncProgress);

        ImageView blockchainSyncIcon = new ImageView();
        blockchainSyncIcon.setVisible(false);
        blockchainSyncIcon.setManaged(false);

        blockchainSyncIconIdListener = (ov, oldValue, newValue) -> {
            blockchainSyncIcon.setId(newValue);
            blockchainSyncIcon.setVisible(true);
            blockchainSyncIcon.setManaged(true);

            blockchainSyncIndicator.setVisible(false);
            blockchainSyncIndicator.setManaged(false);
        };
        model.blockchainSyncIconId.addListener(blockchainSyncIconIdListener);

        Label bitcoinNetworkLabel = new Label();
        bitcoinNetworkLabel.setText(model.bitcoinNetworkAsString);
        bitcoinNetworkLabel.setId("splash-bitcoin-network-label");

        HBox blockchainSyncBox = new HBox();
        blockchainSyncBox.setSpacing(10);
        blockchainSyncBox.setAlignment(Pos.CENTER);
        blockchainSyncBox.setPadding(new Insets(40, 0, 0, 0));
        blockchainSyncBox.setPrefHeight(50);
        blockchainSyncBox.getChildren().addAll(blockchainSyncLabel, blockchainSyncIndicator,
                blockchainSyncIcon, bitcoinNetworkLabel);


        // create P2PNetworkBox
        splashP2PNetworkLabel = new Label();
        splashP2PNetworkLabel.setWrapText(true);
        splashP2PNetworkLabel.setMaxWidth(500);
        splashP2PNetworkLabel.setTextAlignment(TextAlignment.CENTER);
        splashP2PNetworkLabel.textProperty().bind(model.splashP2PNetworkInfo);

        splashP2PNetworkIndicator = new ProgressIndicator();
        splashP2PNetworkIndicator.setMaxSize(24, 24);
        splashP2PNetworkIndicator.progressProperty().bind(model.splashP2PNetworkProgress);

        splashP2PNetworkErrorMsgListener = (ov, oldValue, newValue) -> {
            splashP2PNetworkLabel.setId("splash-error-state-msg");
            splashP2PNetworkIndicator.setVisible(false);
        };
        model.p2PNetworkErrorMsg.addListener(splashP2PNetworkErrorMsgListener);


        ImageView splashP2PNetworkIcon = new ImageView();
        splashP2PNetworkIcon.setId("image-connection-tor");
        splashP2PNetworkIcon.setVisible(false);
        splashP2PNetworkIcon.setManaged(false);
        HBox.setMargin(splashP2PNetworkIcon, new Insets(0, 0, 5, 0));

        splashP2PNetworkIconIdListener = (ov, oldValue, newValue) -> {
            splashP2PNetworkIcon.setId(newValue);
            splashP2PNetworkIcon.setVisible(true);
            splashP2PNetworkIcon.setManaged(true);
        };
        model.p2PNetworkIconId.addListener(splashP2PNetworkIconIdListener);

        splashP2PNetworkProgressListener = (ov, oldValue, newValue) -> {
            if ((double) newValue >= 1) {
                splashP2PNetworkIndicator.setVisible(false);
                splashP2PNetworkIndicator.setManaged(false);
            }
        };
        model.splashP2PNetworkProgress.addListener(splashP2PNetworkProgressListener);

        HBox splashP2PNetworkBox = new HBox();
        splashP2PNetworkBox.setSpacing(10);
        splashP2PNetworkBox.setAlignment(Pos.CENTER);
        splashP2PNetworkBox.setPrefHeight(50);
        splashP2PNetworkBox.getChildren().addAll(splashP2PNetworkLabel, splashP2PNetworkIndicator, splashP2PNetworkIcon);

        vBox.getChildren().addAll(logo, blockchainSyncBox, splashP2PNetworkBox);
        return vBox;
    }

    private void disposeSplashScreen() {
        model.walletServiceErrorMsg.removeListener(walletServiceErrorMsgListener);
        model.blockchainSyncIconId.removeListener(blockchainSyncIconIdListener);

        model.p2PNetworkErrorMsg.removeListener(splashP2PNetworkErrorMsgListener);
        model.p2PNetworkIconId.removeListener(splashP2PNetworkIconIdListener);
        model.splashP2PNetworkProgress.removeListener(splashP2PNetworkProgressListener);

        blockchainSyncLabel.textProperty().unbind();
        blockchainSyncIndicator.progressProperty().unbind();

        splashP2PNetworkLabel.textProperty().unbind();
        splashP2PNetworkIndicator.progressProperty().unbind();

        model.onSplashScreenRemoved();
    }


    private AnchorPane createFooter() {
        // line
        Separator separator = new Separator();
        separator.setId("footer-pane-line");
        separator.setPrefHeight(1);
        setLeftAnchor(separator, 0d);
        setRightAnchor(separator, 0d);
        setTopAnchor(separator, 0d);

        // BTC
        Label blockchainSyncLabel = new Label();
        blockchainSyncLabel.setId("footer-pane");
        blockchainSyncLabel.textProperty().bind(model.blockchainSyncInfoFooter);

        ProgressBar blockchainSyncIndicator = new ProgressBar(-1);
        blockchainSyncIndicator.setPrefWidth(120);
        blockchainSyncIndicator.setMaxHeight(10);
        blockchainSyncIndicator.progressProperty().bind(model.blockchainSyncProgress);

        Label bitcoinNetworkLabel = new Label();
        bitcoinNetworkLabel.setText("/ Bitcoin network: " + model.bitcoinNetworkAsString);

        model.walletServiceErrorMsg.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                bitcoinNetworkLabel.setId("splash-error-state-msg");
                bitcoinNetworkLabel.setText("/ Bitcoin network: Not connected");
                openBTCConnectionErrorPopup(newValue);
            } else {
                bitcoinNetworkLabel.setId("footer-bitcoin-network-label");
                bitcoinNetworkLabel.setText("/ Bitcoin network: " + model.bitcoinNetworkAsString);
            }
        });

        model.blockchainSyncProgress.addListener((ov, oldValue, newValue) -> {
            if ((double) newValue >= 1) {
                blockchainSyncIndicator.setVisible(false);
                blockchainSyncIndicator.setManaged(false);
            }
        });

        HBox blockchainSyncBox = new HBox();
        blockchainSyncBox.setSpacing(10);
        blockchainSyncBox.setAlignment(Pos.CENTER);
        blockchainSyncBox.getChildren().addAll(blockchainSyncLabel, blockchainSyncIndicator, bitcoinNetworkLabel);
        setLeftAnchor(blockchainSyncBox, 10d);
        setBottomAnchor(blockchainSyncBox, 7d);

        // version
        Label versionLabel = new Label();
        versionLabel.setId("footer-pane");
        versionLabel.setTextAlignment(TextAlignment.CENTER);
        versionLabel.setAlignment(Pos.BASELINE_CENTER);
        versionLabel.setText(model.version);
        root.widthProperty().addListener((ov, oldValue, newValue) -> {
            versionLabel.setLayoutX(((double) newValue - versionLabel.getWidth()) / 2);
        });
        setBottomAnchor(versionLabel, 7d);


        // P2P Network
        Label p2PNetworkLabel = new Label();
        p2PNetworkLabel.setId("footer-pane");
        setRightAnchor(p2PNetworkLabel, 33d);
        setBottomAnchor(p2PNetworkLabel, 7d);
        p2PNetworkLabel.textProperty().bind(model.p2PNetworkInfo);

        ImageView p2PNetworkIcon = new ImageView();
        setRightAnchor(p2PNetworkIcon, 10d);
        setBottomAnchor(p2PNetworkIcon, 7d);
        p2PNetworkIcon.idProperty().bind(model.p2PNetworkIconId);

        model.p2PNetworkErrorMsg.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                p2PNetworkLabel.setId("splash-error-state-msg");
                p2PNetworkLabel.textProperty().unbind();
                new Popup().error("Connecting to the P2P network failed. \n" + newValue
                        + "\nPlease check your internet connection.").show();
            } else {
                p2PNetworkLabel.setId("footer-pane");
                p2PNetworkLabel.textProperty().bind(model.p2PNetworkInfo);
            }
        });

        AnchorPane footerContainer = new AnchorPane(separator, blockchainSyncBox, versionLabel, p2PNetworkLabel, p2PNetworkIcon) {{
            setId("footer-pane");
            setMinHeight(30);
            setMaxHeight(30);
        }};

        return footerContainer;
    }

    private void setupNotificationIcon(Pane buttonHolder) {
        Label label = new Label();
        label.textProperty().bind(model.numPendingTradesAsString);
        label.relocate(5, 1);
        label.setId("nav-alert-label");

        ImageView icon = new ImageView();
        icon.setLayoutX(0.5);
        icon.setId("image-alert-round");

        Pane notification = new Pane();
        notification.relocate(30, 9);
        notification.setMouseTransparent(true);
        notification.setEffect(new DropShadow(4, 1, 2, Color.GREY));
        notification.getChildren().addAll(icon, label);
        notification.visibleProperty().bind(model.showPendingTradesNotification);
        buttonHolder.getChildren().add(notification);

        model.showPendingTradesNotification.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                SystemNotification.openInfoNotification(title, "You received a new trade message.");
        });
    }

    private void setupDisputesIcon(Pane buttonHolder) {
        Label label = new Label();
        label.textProperty().bind(model.numOpenDisputesAsString);
        label.relocate(5, 1);
        label.setId("nav-alert-label");

        ImageView icon = new ImageView();
        icon.setLayoutX(0.5);
        icon.setId("image-alert-round");

        Pane notification = new Pane();
        notification.relocate(30, 9);
        notification.setMouseTransparent(true);
        notification.setEffect(new DropShadow(4, 1, 2, Color.GREY));
        notification.getChildren().addAll(icon, label);
        notification.visibleProperty().bind(model.showOpenDisputesNotification);
        buttonHolder.getChildren().add(notification);

        model.showOpenDisputesNotification.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                SystemNotification.openInfoNotification(title, "You received a dispute message.");
        });
    }

    private class NavButton extends ToggleButton {

        private final Class<? extends View> viewClass;

        public NavButton(Class<? extends View> viewClass, String title) {
            super(title, new ImageView() {{
                setId("image-nav-" + viewId(viewClass));
            }});

            this.viewClass = viewClass;

            this.setToggleGroup(navButtons);
            this.setId("nav-button");
            this.setPadding(new Insets(0, -10, -10, -10));
            this.setMinSize(50, 50);
            this.setMaxSize(50, 50);
            this.setContentDisplay(ContentDisplay.TOP);
            this.setGraphicTextGap(0);

            this.selectedProperty().addListener((ov, oldValue, newValue) -> {
                this.setMouseTransparent(newValue);
                this.setMinSize(50, 50);
                this.setMaxSize(50, 50);
                this.setGraphicTextGap(newValue ? -1 : 0);
                if (newValue) {
                    this.getGraphic().setId("image-nav-" + viewId(viewClass) + "-active");
                } else {
                    this.getGraphic().setId("image-nav-" + viewId(viewClass));
                }
            });

            this.setOnAction(e -> navigation.navigateTo(MainView.class, viewClass));
        }

    }

    private static String viewId(Class<? extends View> viewClass) {
        String viewName = viewClass.getSimpleName();
        String suffix = "View";
        int suffixIdx = viewName.indexOf(suffix);
        if (suffixIdx != viewName.length() - suffix.length())
            throw new IllegalArgumentException("Cannot get ID for " + viewClass + ": class must end in " + suffix);
        return viewName.substring(0, suffixIdx).toLowerCase();
    }

    private void openBTCConnectionErrorPopup(String errorMsg) {
        new Popup().error("Connecting to the bitcoin network failed. \n" + errorMsg).show();
    }
}