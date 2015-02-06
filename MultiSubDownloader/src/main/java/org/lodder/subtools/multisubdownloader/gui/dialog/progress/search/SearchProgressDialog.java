package org.lodder.subtools.multisubdownloader.gui.dialog.progress.search;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import org.lodder.subtools.multisubdownloader.gui.dialog.Cancelable;
import org.lodder.subtools.multisubdownloader.gui.dialog.MultiSubDialog;
import org.lodder.subtools.multisubdownloader.subtitleproviders.SubtitleProvider;
import org.lodder.subtools.sublibrary.model.Release;

public class SearchProgressDialog extends MultiSubDialog implements SearchProgressListener {

  private final Cancelable searchAction;
  private SearchProgressTableModel tableModel;
  private JProgressBar progressBar;

  public SearchProgressDialog(JFrame frame, Cancelable searchAction) {
    super(frame, "Searching", false);
    this.searchAction = searchAction;

    initialize_ui();
    setDialogLocation(frame);
    repaint();
    this.setVisible(true);
  }

  @Override
  public void progress(SubtitleProvider provider, int jobsLeft, Release release) {
    this.tableModel.update(provider.getName(), jobsLeft, (release == null ? "Done" : release.getFilename()));
  }

  @Override
  public void progress(int progress) {
    if (progress == 0) {
      this.progressBar.setIndeterminate(true);
    } else {
      this.progressBar.setIndeterminate(false);
      this.progressBar.setValue(progress);
      this.progressBar.setString(Integer.toString(progress));
    }
  }

  private void initialize_ui() {
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        searchAction.cancel(true);
      }
    });
    setBounds(100, 100, 601, 300);
    getContentPane().setLayout(new MigLayout("", "[grow,fill][]", "[][][]"));

    this.tableModel = new SearchProgressTableModel();
    JTable table = new JTable(tableModel);

    table.getColumnModel().getColumn(0).setMaxWidth(150);
    table.getColumnModel().getColumn(1).setMaxWidth(50);

    JScrollPane tablePane = new JScrollPane(table);
    tablePane.setViewportView(table);
    getContentPane().add(tablePane, "cell 0 0 2 1");

    progressBar = new JProgressBar(0, 100);
    progressBar.setIndeterminate(true);
    getContentPane().add(progressBar, "cell 0 1 2 1,grow");
    
        JButton btnStop = new JButton("Stop!");
        btnStop.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent arg0) {
            searchAction.cancel(true);
          }
        });
        getContentPane().add(btnStop, "cell 1 2,alignx left");
  }
}