package org.lodder.subtools.multisubdownloader.subtitleproviders.adapters;

import java.util.ArrayList;
import java.util.List;

import org.lodder.subtools.multisubdownloader.subtitleproviders.SubtitleProvider;
import org.lodder.subtools.multisubdownloader.subtitleproviders.subsmax.JSubsMaxApi;
import org.lodder.subtools.multisubdownloader.subtitleproviders.subsmax.model.SubMaxSubtitleDescriptor;
import org.lodder.subtools.sublibrary.JSubAdapter;
import org.lodder.subtools.sublibrary.control.ReleaseParser;
import org.lodder.subtools.sublibrary.logging.Logger;
import org.lodder.subtools.sublibrary.model.Release;
import org.lodder.subtools.sublibrary.model.TvRelease;
import org.lodder.subtools.sublibrary.model.MovieRelease;
import org.lodder.subtools.sublibrary.model.Subtitle;
import org.lodder.subtools.sublibrary.model.SubtitleMatchType;

public class JSubsMaxAdapter implements JSubAdapter, SubtitleProvider  {

  private static JSubsMaxApi jsmapi;

  public JSubsMaxAdapter() {
    try {
      if (jsmapi == null) {
        jsmapi = new JSubsMaxApi();
      }
    } catch (Exception e) {
      Logger.instance.error("API JSubsMax INIT: " + e.getCause());
    }
  }
  
  @Override
  public String getName() {
    return "SubsMax";
  }

  @Override
  public List<Subtitle> search(Release release, String languageCode) {
    if (release instanceof MovieRelease) {
      return this.searchSubtitles((MovieRelease) release, languageCode);
    } else if (release instanceof TvRelease){
      return this.searchSubtitles((TvRelease) release, languageCode);
    }
    return new ArrayList<Subtitle>();
  }

  @Override
  public List<Subtitle> searchSubtitles(TvRelease tvRelease, String... sublanguageids) {
    String showName = "";
    if (tvRelease.getOriginalShowName().length() > 0) {
      showName = tvRelease.getOriginalShowName();
    } else {
      showName = tvRelease.getShow();
    }
    
    List<SubMaxSubtitleDescriptor> lSubtitles =
        jsmapi.searchSubtitles(showName, tvRelease.getSeason(), tvRelease
            .getEpisodeNumbers().get(0), sublanguageids[0]);
    
    List<Subtitle> listFoundSubtitles = new ArrayList<Subtitle>();
    
    for (SubMaxSubtitleDescriptor sub:lSubtitles){
      listFoundSubtitles.add(new Subtitle(Subtitle.SubtitleSource.SUBSMAX, sub.getFilename(),
        sub.getLink(), sublanguageids[0], "", SubtitleMatchType.EVERYTHING, ReleaseParser
            .extractReleasegroup(sub.getFilename()), "", false));
    }
    
    return listFoundSubtitles;
  }

  @Override
  public List<Subtitle> searchSubtitles(MovieRelease movieRelease, String... sublanguageids) {
    // TODO Auto-generated method stub
    return null;
  }

}
