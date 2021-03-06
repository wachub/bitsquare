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

package io.bitsquare.gui.main.portfolio.failedtrades;

import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.popups.TradeDetailsPopup;
import io.bitsquare.trade.Trade;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import javax.inject.Inject;

@FxmlView
public class FailedTradesView extends ActivatableViewAndModel<VBox, FailedTradesViewModel> {

    @FXML TableView<FailedTradesListItem> table;
    @FXML TableColumn<FailedTradesListItem, FailedTradesListItem> priceColumn, amountColumn, volumeColumn,
            directionColumn, dateColumn, tradeIdColumn, stateColumn;
    private final TradeDetailsPopup tradeDetailsPopup;

    @Inject
    public FailedTradesView(FailedTradesViewModel model, TradeDetailsPopup tradeDetailsPopup) {
        super(model);
        this.tradeDetailsPopup = tradeDetailsPopup;
    }

    @Override
    public void initialize() {
        setTradeIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setStateColumnCellFactory();

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No closed trades available"));
    }

    @Override
    protected void activate() {
        table.setItems(model.getList());
    }

    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {

                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(TableColumn<FailedTradesListItem,
                            FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            private Hyperlink hyperlink;

                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    hyperlink = new Hyperlink(model.getTradeId(item));
                                    Tooltip.install(hyperlink, new Tooltip(model.getTradeId(item)));
                                    hyperlink.setOnAction(event -> {
                                        Trade trade = item.getTrade();
                                        tradeDetailsPopup.show(trade);
                                    });
                                    setGraphic(hyperlink);
                                }
                                else {
                                    setGraphic(null);
                                    setId(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getDate(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setStateColumnCellFactory() {
        stateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        stateColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getState(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }


    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getAmount(item));
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getPrice(item));
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getVolume(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<FailedTradesListItem, FailedTradesListItem>, TableCell<FailedTradesListItem,
                        FailedTradesListItem>>() {
                    @Override
                    public TableCell<FailedTradesListItem, FailedTradesListItem> call(
                            TableColumn<FailedTradesListItem, FailedTradesListItem> column) {
                        return new TableCell<FailedTradesListItem, FailedTradesListItem>() {
                            @Override
                            public void updateItem(final FailedTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getDirectionLabel(item));
                            }
                        };
                    }
                });
    }
}

