package org.lodder.subtools.multisubdownloader.gui.dialog.progress.fileindexer;

import org.lodder.subtools.multisubdownloader.MainWindow;
import org.lodder.subtools.multisubdownloader.gui.actions.ActionException;
import org.lodder.subtools.multisubdownloader.gui.dialog.Cancelable;
import org.lodder.subtools.multisubdownloader.gui.dialog.ProgressDialog;
import org.lodder.subtools.multisubdownloader.gui.extra.progress.StatusMessenger;

public class IndexingProgressDialog extends ProgressDialog implements IndexingProgressListener {

  private final MainWindow window;
  private boolean completed;

  public IndexingProgressDialog(MainWindow window, Cancelable sft) {
    super(window, sft);
    this.window = window;
    this.completed = false;
    StatusMessenger.instance.removeListener(this);
  }

  @Override
  public void progress(int progress) {
    this.setVisible();
    updateProgress(progress);
  }

  @Override
  public void progress(String directory) {
    this.setVisible();
    setMessage(directory);
  }

  @Override
  public void completed() {
    this.setVisible(false);
  }

  @Override
  public void onError(ActionException exception) {
    this.setVisible(false);
    this.window.showErrorMessage(exception.getMessage());
  }

  @Override
  public void onStatus(String message) {
    this.window.setStatusMessage(message);
  }

  private void setVisible() {
    if (this.completed) {
      return;
    }
    this.setVisible(true);
  }
}
