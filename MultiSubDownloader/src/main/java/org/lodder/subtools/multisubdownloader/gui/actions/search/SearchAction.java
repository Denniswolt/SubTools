package org.lodder.subtools.multisubdownloader.gui.actions.search;

import java.util.List;

import org.lodder.subtools.multisubdownloader.Messages;
import org.lodder.subtools.multisubdownloader.gui.actions.ActionException;
import org.lodder.subtools.multisubdownloader.gui.dialog.Cancelable;
import org.lodder.subtools.multisubdownloader.gui.dialog.progress.StatusListener;
import org.lodder.subtools.multisubdownloader.gui.dialog.progress.fileindexer.IndexingProgressListener;
import org.lodder.subtools.multisubdownloader.gui.dialog.progress.search.SearchProgressListener;
import org.lodder.subtools.multisubdownloader.settings.model.Settings;
import org.lodder.subtools.multisubdownloader.subtitleproviders.SubtitleProvider;
import org.lodder.subtools.multisubdownloader.subtitleproviders.SubtitleProviderStore;
import org.lodder.subtools.multisubdownloader.workers.SearchHandler;
import org.lodder.subtools.multisubdownloader.workers.SearchManager;
import org.lodder.subtools.sublibrary.logging.Logger;
import org.lodder.subtools.sublibrary.model.Release;

public abstract class SearchAction extends Thread implements Cancelable, SearchHandler {

  protected Settings settings;
  protected SubtitleProviderStore subtitleProviderStore;
  protected SearchManager searchManager;
  protected List<Release> releases;
  protected IndexingProgressListener indexingProgressListener;
  protected SearchProgressListener searchProgressListener;
  protected StatusListener statusListener;

  public void setSettings(Settings settings) {
    this.settings = settings;
  }

  public void setProviderStore(SubtitleProviderStore store) {
    this.subtitleProviderStore = store;
  }

  public void setStatusListener(StatusListener listener) {
    this.statusListener = listener;
  }

  public void setSearchProgressListener(SearchProgressListener listener) {
    this.searchProgressListener = listener;
  }

  public void setIndexingProgressListener(IndexingProgressListener listener) {
    this.indexingProgressListener = listener;
  }

  @Override
  public void run() {
    Logger.instance
        .trace(this.getClass().getSimpleName(), "run", "SearchAction is being executed");
    try {
      this.search();
    } catch (ActionException e) {
      Logger.instance.error(e.getMessage());
      if (this.statusListener != null) {
        this.statusListener.onError(e);
      }
    }
  }

  private void search() throws ActionException {
    this.setStatusListener(this.indexingProgressListener);

    validate();

    String languageCode = this.getLanguageCode();

    setStatusMessage(Messages.getString("SearchAction.StatusIndexing"));

    this.releases = createReleases();

    if (this.releases.size() <= 0) {
      this.cancel(true);
      return;
    }

    this.indexingProgressListener.completed();

    this.setStatusListener(this.searchProgressListener);

    /* Create a new SearchManager. */
    this.searchManager = new SearchManager(this.settings);

    /* Tell the manager which language we want */
    this.searchManager.setLanguage(languageCode);

    /* Tell the manager which providers to use */
    for (SubtitleProvider subtitleProvider : this.subtitleProviderStore.getAllProviders()) {
      if (!settings.isSerieSource(subtitleProvider.getName())) {
        continue;
      }

      this.searchManager.addProvider(subtitleProvider);
    }

    /* Tell the manager which releases to search. */
    for (Release release : this.releases) {
      this.searchManager.addRelease(release);
    }

    /* Listen for when the manager tells us Subtitles are found */
    this.searchManager.onFound(this);

    /* Tell the manager where to push progressUpdates */
    this.searchManager.setProgressListener(this.searchProgressListener);

    setStatusMessage(Messages.getString("SearchAction.StatusSearching"));

    /* Tell the manager to start searching */
    this.searchManager.start();
  }

  protected abstract List<Release> createReleases() throws ActionException;

  protected void setStatusMessage(String message) {
    this.statusListener.onStatus(message);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    this.searchManager.cancel(mayInterruptIfRunning);
    this.interrupt();
    this.indexingProgressListener.completed();
    this.searchProgressListener.completed();
    return true;
  }

  protected abstract String getLanguageCode();

  protected String getLanguageCode(String language) {
    if (language.equals("Nederlands")) {
      return "nl";
    } else if (language.equals("Engels")) {
      return "en";
    }
    return null;
  }

  protected void validate() throws SearchSetupException {
    if (this.settings == null) {
      throw new SearchSetupException("Settings must be set.");
    }
    if (this.subtitleProviderStore == null) {
      throw new SearchSetupException("SubtitleProviderStore must be set.");
    }
    if (this.searchProgressListener == null) {
      throw new SearchSetupException("SearchProgressListener must be set.");
    }
    if (this.indexingProgressListener == null) {
      throw new SearchSetupException("IndexingProgressListener must be set.");
    }
    if (this.statusListener == null) {
      throw new SearchSetupException("StatusListener must be set.");
    }
  }

}
