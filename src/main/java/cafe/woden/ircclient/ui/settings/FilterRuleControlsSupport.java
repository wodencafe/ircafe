package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.FilterRule;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import cafe.woden.ircclient.ui.filter.FilterRuleEntryDialog;
import cafe.woden.ircclient.ui.filter.FilterSettings;
import cafe.woden.ircclient.ui.filter.FilterSettingsBus;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultCellEditor;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.table.TableColumn;

final class FilterRuleControlsSupport {
  private FilterRuleControlsSupport() {}

  static FilterRuleControls buildControls(
      FilterSettings current,
      Window owner,
      FilterSettingsBus filterSettingsBus,
      RuntimeConfigStore runtimeConfig,
      ActiveTargetPort targetCoordinator,
      TranscriptRebuildService transcriptRebuildService,
      List<AutoCloseable> closeables) {
    FilterRulesTableModel rulesModel = new FilterRulesTableModel();
    rulesModel.setRules(current.rules());

    JTable rulesTable = new JTable(rulesModel);
    rulesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    try {
      rulesTable.setDragEnabled(true);
      rulesTable.setDropMode(DropMode.INSERT_ROWS);
    } catch (Exception ignored) {
    }
    try {
      TableColumn onCol = rulesTable.getColumnModel().getColumn(0);
      onCol.setMinWidth(42);
      onCol.setMaxWidth(50);
      onCol.setPreferredWidth(45);
      onCol.setCellRenderer(new CenteredBooleanRenderer());
      JCheckBox cb = new JCheckBox();
      cb.setHorizontalAlignment(SwingConstants.CENTER);
      cb.setBorderPainted(false);
      onCol.setCellEditor(new DefaultCellEditor(cb));
    } catch (Exception ignored) {
    }

    PropertyChangeListener rulesListener =
        evt -> {
          if (!FilterSettingsBus.PROP_FILTER_SETTINGS.equals(evt.getPropertyName())) return;
          Object nv = evt.getNewValue();
          if (!(nv instanceof FilterSettings fs)) return;

          SwingUtilities.invokeLater(
              () -> {
                try {
                  java.util.UUID selectedId = null;
                  int selectedRow = rulesTable.getSelectedRow();
                  if (selectedRow >= 0) {
                    FilterRule selected = rulesModel.ruleAt(selectedRow);
                    if (selected != null) selectedId = selected.id();
                  }

                  rulesModel.setRules(fs.rules());

                  if (selectedId != null) {
                    for (int i = 0; i < rulesModel.getRowCount(); i++) {
                      FilterRule r = rulesModel.ruleAt(i);
                      if (r != null && selectedId.equals(r.id())) {
                        rulesTable.getSelectionModel().setSelectionInterval(i, i);
                        rulesTable.scrollRectToVisible(rulesTable.getCellRect(i, 0, true));
                        break;
                      }
                    }
                  }
                } catch (Exception ignored) {
                }
              });
        };
    filterSettingsBus.addListener(rulesListener);
    if (closeables != null) {
      closeables.add(() -> filterSettingsBus.removeListener(rulesListener));
    }

    rulesModel.addTableModelListener(
        ev -> {
          try {
            if (ev.getColumn() != 0) return;
            int row = ev.getFirstRow();
            if (row < 0) return;
            FilterRule edited = rulesModel.ruleAt(row);
            if (edited == null) return;

            FilterSettings snap = filterSettingsBus.get();
            if (snap == null) return;

            List<FilterRule> nextRules = new ArrayList<>();
            boolean replaced = false;
            if (snap.rules() != null) {
              for (FilterRule r : snap.rules()) {
                if (!replaced && r != null && edited.id() != null && edited.id().equals(r.id())) {
                  nextRules.add(edited);
                  replaced = true;
                } else {
                  nextRules.add(r);
                }
              }
            }

            if (!replaced && row >= 0 && row < nextRules.size()) {
              nextRules.set(row, edited);
              replaced = true;
            }
            if (!replaced) {
              nextRules.add(edited);
            }

            applyRules(
                withRules(snap, nextRules),
                filterSettingsBus,
                runtimeConfig,
                targetCoordinator,
                transcriptRebuildService);
          } catch (Exception ignored) {
          }
        });

    JButton addRule = new JButton("Add rule...");
    JButton editRule = new JButton("Edit...");
    JButton deleteRule = new JButton("Delete");
    JButton moveRuleUp = new JButton("Move up");
    JButton moveRuleDown = new JButton("Move down");
    PreferencesDialog.configureIconOnlyButton(addRule, "plus", "Add filter rule");
    PreferencesDialog.configureIconOnlyButton(editRule, "edit", "Edit selected filter rule");
    PreferencesDialog.configureIconOnlyButton(deleteRule, "trash", "Delete selected filter rule");
    PreferencesDialog.configureIconOnlyButton(
        moveRuleUp, "arrow-up", "Move selected filter rule up");
    PreferencesDialog.configureIconOnlyButton(
        moveRuleDown, "arrow-down", "Move selected filter rule down");
    editRule.setEnabled(false);
    deleteRule.setEnabled(false);
    moveRuleUp.setEnabled(false);
    moveRuleDown.setEnabled(false);

    Runnable refreshRuleButtons =
        () -> {
          int row = rulesTable.getSelectedRow();
          boolean has = row >= 0 && rulesModel.ruleAt(row) != null;
          editRule.setEnabled(has);
          deleteRule.setEnabled(has);
          if (!has) {
            moveRuleUp.setEnabled(false);
            moveRuleDown.setEnabled(false);
            return;
          }
          moveRuleUp.setEnabled(row > 0);
          moveRuleDown.setEnabled(row < (rulesModel.getRowCount() - 1));
        };

    rulesTable.getSelectionModel().addListSelectionListener(e -> refreshRuleButtons.run());

    try {
      class RuleRowTransferHandler extends TransferHandler {
        private final DataFlavor rowFlavor;

        RuleRowTransferHandler() {
          try {
            rowFlavor =
                new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=java.lang.Integer");
          } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
          }
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
          if (!(c instanceof JTable t)) return null;
          int row = t.getSelectedRow();
          if (row < 0) return null;
          final Integer payload = row;
          return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
              return new DataFlavor[] {rowFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
              return rowFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
              if (!isDataFlavorSupported(flavor)) return null;
              return payload;
            }
          };
        }

        @Override
        public int getSourceActions(JComponent c) {
          return MOVE;
        }

        @Override
        public boolean canImport(TransferSupport support) {
          if (!support.isDrop()) return false;
          if (!(support.getComponent() instanceof JTable)) return false;
          support.setShowDropLocation(true);
          return support.isDataFlavorSupported(rowFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
          if (!canImport(support)) return false;
          if (!(support.getComponent() instanceof JTable target)) return false;
          if (!(support.getDropLocation() instanceof JTable.DropLocation dl)) return false;

          int dropViewRow = dl.getRow();
          if (dropViewRow < 0) dropViewRow = target.getRowCount();

          Integer fromViewRow;
          try {
            Object o = support.getTransferable().getTransferData(rowFlavor);
            if (!(o instanceof Integer i)) return false;
            fromViewRow = i;
          } catch (Exception ex) {
            return false;
          }

          if (fromViewRow == dropViewRow || fromViewRow + 1 == dropViewRow) return false;

          int fromModelRow = target.convertRowIndexToModel(fromViewRow);
          int dropModelRow =
              dropViewRow >= target.getRowCount()
                  ? target.getRowCount()
                  : target.convertRowIndexToModel(dropViewRow);

          FilterSettings snap = filterSettingsBus.get();
          if (snap == null || snap.rules() == null) return false;

          List<FilterRule> nextRules = new ArrayList<>(snap.rules());
          if (fromModelRow < 0 || fromModelRow >= nextRules.size()) return false;

          FilterRule moving = nextRules.remove(fromModelRow);
          if (dropModelRow > fromModelRow) dropModelRow--;
          dropModelRow = Math.max(0, Math.min(dropModelRow, nextRules.size()));
          nextRules.add(dropModelRow, moving);

          FilterSettings next = withRules(snap, nextRules);
          applyRules(
              next, filterSettingsBus, runtimeConfig, targetCoordinator, transcriptRebuildService);

          final int newRow = dropModelRow;
          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  if (newRow >= 0 && newRow < rulesModel.getRowCount()) {
                    rulesTable.getSelectionModel().setSelectionInterval(newRow, newRow);
                    rulesTable.scrollRectToVisible(rulesTable.getCellRect(newRow, 0, true));
                  }
                  refreshRuleButtons.run();
                } catch (Exception ignored) {
                }
              });

          return true;
        }
      }

      rulesTable.setTransferHandler(new RuleRowTransferHandler());
    } catch (Exception ignored) {
    }

    Runnable moveSelectedRuleUp =
        () -> {
          int row = rulesTable.getSelectedRow();
          if (row <= 0) return;

          int newRow = row - 1;
          FilterSettings snap = filterSettingsBus.get();
          if (snap == null || snap.rules() == null) return;

          List<FilterRule> nextRules = new ArrayList<>(snap.rules());
          if (row >= nextRules.size() || newRow < 0) return;
          java.util.Collections.swap(nextRules, row, newRow);

          FilterSettings next = withRules(snap, nextRules);
          applyRules(
              next, filterSettingsBus, runtimeConfig, targetCoordinator, transcriptRebuildService);

          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  rulesTable.getSelectionModel().setSelectionInterval(newRow, newRow);
                  rulesTable.scrollRectToVisible(rulesTable.getCellRect(newRow, 0, true));
                  refreshRuleButtons.run();
                } catch (Exception ignored) {
                }
              });
        };

    Runnable moveSelectedRuleDown =
        () -> {
          int row = rulesTable.getSelectedRow();
          if (row < 0 || row >= rulesModel.getRowCount() - 1) return;

          int newRow = row + 1;
          FilterSettings snap = filterSettingsBus.get();
          if (snap == null || snap.rules() == null) return;

          List<FilterRule> nextRules = new ArrayList<>(snap.rules());
          if (newRow >= nextRules.size()) return;
          java.util.Collections.swap(nextRules, row, newRow);

          FilterSettings next = withRules(snap, nextRules);
          applyRules(
              next, filterSettingsBus, runtimeConfig, targetCoordinator, transcriptRebuildService);

          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  rulesTable.getSelectionModel().setSelectionInterval(newRow, newRow);
                  rulesTable.scrollRectToVisible(rulesTable.getCellRect(newRow, 0, true));
                  refreshRuleButtons.run();
                } catch (Exception ignored) {
                }
              });
        };

    moveRuleUp.addActionListener(e -> moveSelectedRuleUp.run());
    moveRuleDown.addActionListener(e -> moveSelectedRuleDown.run());

    Runnable openEditRule =
        () -> {
          int row = rulesTable.getSelectedRow();
          if (row < 0) return;
          FilterRule seed = rulesModel.ruleAt(row);
          if (seed == null) return;

          FilterSettings snap = filterSettingsBus.get();
          Set<String> reserved = new HashSet<>();
          if (snap != null && snap.rules() != null) {
            for (FilterRule r : snap.rules()) {
              if (r == null) continue;
              if (seed.id() != null && seed.id().equals(r.id())) continue;
              reserved.add(r.nameKey());
            }
          }

          var edited =
              FilterRuleEntryDialog.open(
                  owner, "Edit Filter Rule", seed, reserved, seed.scopePattern());
          if (edited.isEmpty()) return;

          List<FilterRule> nextRules = new ArrayList<>();
          boolean replaced = false;
          if (snap != null && snap.rules() != null) {
            for (FilterRule r : snap.rules()) {
              if (!replaced && r != null && seed.id() != null && seed.id().equals(r.id())) {
                nextRules.add(edited.get());
                replaced = true;
              } else {
                nextRules.add(r);
              }
            }
          }
          if (!replaced && row >= 0 && row < nextRules.size()) {
            nextRules.set(row, edited.get());
            replaced = true;
          }
          if (!replaced) nextRules.add(edited.get());

          FilterSettings next = withRules(snap, nextRules);
          applyRules(
              next, filterSettingsBus, runtimeConfig, targetCoordinator, transcriptRebuildService);

          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  int idx = -1;
                  for (int i = 0; i < next.rules().size(); i++) {
                    FilterRule r = next.rules().get(i);
                    if (r != null
                        && edited.get().id() != null
                        && edited.get().id().equals(r.id())) {
                      idx = i;
                      break;
                    }
                  }
                  if (idx < 0) idx = Math.max(0, Math.min(row, rulesModel.getRowCount() - 1));
                  if (idx >= 0 && idx < rulesModel.getRowCount()) {
                    rulesTable.getSelectionModel().setSelectionInterval(idx, idx);
                    rulesTable.scrollRectToVisible(rulesTable.getCellRect(idx, 0, true));
                  }
                  refreshRuleButtons.run();
                } catch (Exception ignored) {
                }
              });
        };

    editRule.addActionListener(e -> openEditRule.run());

    deleteRule.addActionListener(
        e -> {
          int row = rulesTable.getSelectedRow();
          if (row < 0) return;
          FilterRule seed = rulesModel.ruleAt(row);
          if (seed == null) return;

          int confirm =
              JOptionPane.showConfirmDialog(
                  owner,
                  "Delete filter rule '" + seed.name() + "'?",
                  "Delete Filter Rule",
                  JOptionPane.OK_CANCEL_OPTION);
          if (confirm != JOptionPane.OK_OPTION) return;

          FilterSettings snap = filterSettingsBus.get();
          List<FilterRule> nextRules = new ArrayList<>();
          boolean removed = false;
          if (snap != null && snap.rules() != null) {
            for (FilterRule r : snap.rules()) {
              if (!removed && r != null && seed.id() != null && seed.id().equals(r.id())) {
                removed = true;
                continue;
              }
              nextRules.add(r);
            }
          }

          if (!removed && row >= 0 && row < nextRules.size()) {
            nextRules.remove(row);
            removed = true;
          }
          if (!removed) return;

          FilterSettings next = withRules(snap, nextRules);
          applyRules(
              next, filterSettingsBus, runtimeConfig, targetCoordinator, transcriptRebuildService);

          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  int nextRow = Math.min(row, Math.max(0, rulesModel.getRowCount() - 1));
                  if (rulesModel.getRowCount() > 0) {
                    rulesTable.getSelectionModel().setSelectionInterval(nextRow, nextRow);
                    rulesTable.scrollRectToVisible(rulesTable.getCellRect(nextRow, 0, true));
                  }
                  refreshRuleButtons.run();
                } catch (Exception ignored) {
                }
              });
        });

    rulesTable.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
              openEditRule.run();
            }
          }
        });

    addRule.addActionListener(
        e -> {
          FilterSettings snap = filterSettingsBus.get();

          String suggestedScope = "*";
          try {
            TargetRef active = targetCoordinator.getActiveTarget();
            if (active != null && !active.isUiOnly()) {
              suggestedScope =
                  active.isStatus() ? "*/status" : active.serverId() + "/" + active.target();
            }
          } catch (Exception ignored) {
          }

          Set<String> reserved = new HashSet<>();
          if (snap != null && snap.rules() != null) {
            for (FilterRule r : snap.rules()) {
              if (r != null) reserved.add(r.nameKey());
            }
          }

          var created =
              FilterRuleEntryDialog.open(owner, "Add Filter Rule", null, reserved, suggestedScope);
          if (created.isEmpty()) return;

          List<FilterRule> nextRules = new ArrayList<>();
          if (snap != null && snap.rules() != null) {
            nextRules.addAll(snap.rules());
          }
          nextRules.add(created.get());

          FilterSettings next = withRules(snap, nextRules);
          applyRules(
              next, filterSettingsBus, runtimeConfig, targetCoordinator, transcriptRebuildService);

          SwingUtilities.invokeLater(
              () -> {
                try {
                  rulesModel.setRules(next.rules());
                  int row = rulesModel.getRowCount() - 1;
                  if (row >= 0) {
                    rulesTable.getSelectionModel().setSelectionInterval(row, row);
                    rulesTable.scrollRectToVisible(rulesTable.getCellRect(row, 0, true));
                  }
                } catch (Exception ignored) {
                }
              });
        });

    return new FilterRuleControls(
        rulesTable, addRule, editRule, deleteRule, moveRuleUp, moveRuleDown);
  }

  private static FilterSettings withRules(FilterSettings snap, List<FilterRule> rules) {
    return new FilterSettings(
        snap != null ? snap.filtersEnabledByDefault() : true,
        snap != null ? snap.placeholdersEnabledByDefault() : true,
        snap != null ? snap.placeholdersCollapsedByDefault() : true,
        snap != null ? snap.placeholderMaxPreviewLines() : 3,
        snap != null ? snap.placeholderMaxLinesPerRun() : 250,
        snap != null ? snap.placeholderTooltipMaxTags() : 12,
        snap != null ? snap.historyPlaceholderMaxRunsPerBatch() : 10,
        snap != null ? snap.historyPlaceholdersEnabledByDefault() : true,
        rules,
        snap != null ? snap.overrides() : List.of());
  }

  private static void applyRules(
      FilterSettings next,
      FilterSettingsBus filterSettingsBus,
      RuntimeConfigStore runtimeConfig,
      ActiveTargetPort targetCoordinator,
      TranscriptRebuildService transcriptRebuildService) {
    filterSettingsBus.set(next);
    runtimeConfig.rememberFilterRules(next.rules());
    try {
      TargetRef active = targetCoordinator.getActiveTarget();
      if (active != null) transcriptRebuildService.rebuild(active);
    } catch (Exception ignored) {
    }
  }
}

final class FilterRuleControls {
  final JTable table;
  final JButton addRule;
  final JButton editRule;
  final JButton deleteRule;
  final JButton moveRuleUp;
  final JButton moveRuleDown;

  FilterRuleControls(
      JTable table,
      JButton addRule,
      JButton editRule,
      JButton deleteRule,
      JButton moveRuleUp,
      JButton moveRuleDown) {
    this.table = table;
    this.addRule = addRule;
    this.editRule = editRule;
    this.deleteRule = deleteRule;
    this.moveRuleUp = moveRuleUp;
    this.moveRuleDown = moveRuleDown;
  }
}
