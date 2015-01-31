package org.lodder.subtools.multisubdownloader.serviceproviders;

import org.lodder.subtools.multisubdownloader.framework.Container;
import org.lodder.subtools.multisubdownloader.framework.service.providers.ServiceProvider;
import org.lodder.subtools.multisubdownloader.subtitleproviders.SubtitleProvider;
import org.lodder.subtools.multisubdownloader.subtitleproviders.SubtitleProviderStore;
import org.lodder.subtools.multisubdownloader.subtitleproviders.adapters.JPodnapisiAdapter;

public class PodnapisiServiceProvider implements ServiceProvider {
  @Override
  public int getPriority() {
    /* We define a priority lower than SubtitleServiceProvider */
    return 1;
  }

  @Override
  public void register(Container app) {
    /* Resolve the SubtitleProviderStore from the IoC Container */
    SubtitleProviderStore subtitleProviderStore = (SubtitleProviderStore) app.make("SubtitleProviderStore");

    /* Create the SubtitleProvider */
    SubtitleProvider podnapisiAdapter = new JPodnapisiAdapter();

    /* Add the SubtitleProvider to the store */
    subtitleProviderStore.addProvider(podnapisiAdapter);
  }
}
