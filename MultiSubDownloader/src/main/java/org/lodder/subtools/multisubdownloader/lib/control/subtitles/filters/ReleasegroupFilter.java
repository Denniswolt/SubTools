package org.lodder.subtools.multisubdownloader.lib.control.subtitles.filters;

import java.util.ArrayList;
import java.util.List;

import org.lodder.subtools.sublibrary.control.ReleaseParser;
import org.lodder.subtools.sublibrary.logging.Logger;
import org.lodder.subtools.sublibrary.model.Release;
import org.lodder.subtools.sublibrary.model.Subtitle;
import org.lodder.subtools.sublibrary.model.SubtitleMatchType;

public class ReleasegroupFilter extends Filter {

  @Override
  public List<Subtitle> doFilter(Release release, List<Subtitle> Subtitles) {
    List<Subtitle> filteredList = new ArrayList<Subtitle>();

    for (Subtitle subtitle : Subtitles) {
      if (subtitle.getReleasegroup().toLowerCase().contains(release.getReleasegroup().toLowerCase())) {
        Logger.instance.debug("getSubtitlesFiltered: found KEYWORD based TEAM match: "
            + subtitle.getFilename());
        
        subtitle.setQuality(ReleaseParser.getQualityKeyword(subtitle.getFilename()));
        subtitle.setSubtitleMatchType(SubtitleMatchType.TEAM);
        
        filteredList.add(subtitle);
      }
    }

    return filteredList;
  }

}