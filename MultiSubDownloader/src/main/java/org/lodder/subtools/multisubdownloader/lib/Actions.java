package org.lodder.subtools.multisubdownloader.lib;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.lodder.subtools.multisubdownloader.gui.dialog.progress.fileindexer.IndexingProgressListener;
import org.lodder.subtools.multisubdownloader.lib.control.subtitles.sorting.SubtitleComparator;
import org.lodder.subtools.multisubdownloader.lib.library.FilenameLibraryBuilder;
import org.lodder.subtools.multisubdownloader.lib.library.LibraryActionType;
import org.lodder.subtools.multisubdownloader.lib.library.LibraryOtherFileActionType;
import org.lodder.subtools.multisubdownloader.lib.library.PathLibraryBuilder;
import org.lodder.subtools.multisubdownloader.settings.model.LibrarySettings;
import org.lodder.subtools.multisubdownloader.settings.model.Settings;
import org.lodder.subtools.multisubdownloader.settings.model.SettingsExcludeItem;
import org.lodder.subtools.multisubdownloader.settings.model.SettingsExcludeType;
import org.lodder.subtools.sublibrary.DetectLanguage;
import org.lodder.subtools.sublibrary.control.ReleaseParser;
import org.lodder.subtools.sublibrary.control.VideoPatterns;
import org.lodder.subtools.sublibrary.logging.Logger;
import org.lodder.subtools.sublibrary.model.Subtitle;
import org.lodder.subtools.sublibrary.model.Subtitle.SubtitleSource;
import org.lodder.subtools.sublibrary.model.SubtitleMatchType;
import org.lodder.subtools.sublibrary.model.Release;
import org.lodder.subtools.sublibrary.model.VideoType;
import org.lodder.subtools.sublibrary.privateRepo.PrivateRepoIndex;
import org.lodder.subtools.sublibrary.util.FilenameContainsFilter;
import org.lodder.subtools.sublibrary.util.FilenameExtensionFilter;
import org.lodder.subtools.sublibrary.util.Files;
import org.lodder.subtools.sublibrary.util.NamedMatcher;
import org.lodder.subtools.sublibrary.util.NamedPattern;
import org.lodder.subtools.sublibrary.util.StringUtils;
import org.lodder.subtools.sublibrary.util.http.DropBoxClient;
import org.lodder.subtools.sublibrary.util.http.HttpClient;

public class Actions {

  private final Settings settings;
  private final boolean usingCMD;
  private IndexingProgressListener indexingProgressListener;
  private int progressFileIndex;
  private int progressFilesTotal;

  public Actions(Settings settings, final boolean usingCMD) {
    this.settings = settings;
    this.usingCMD = usingCMD;
  }

  public int determineWhatSubtitleDownload(final Release release,
      final boolean subtitleSelectionDialog) {

    SubtitleSelection subSelection;
    if (usingCMD)
      subSelection = new SubtitleSelectionCLI(settings);
    else
      subSelection = new SubtitleSelectionGUI(settings);
    

    // Sort subtitles by score
    Collections.sort(release.getMatchingSubs(), new SubtitleComparator());

    if (release.getMatchingSubs().size() > 0) {
      Logger.instance.debug("determineWhatSubtitleDownload for videoFile: " + release.getFilename()
          + " # found subs: " + release.getMatchingSubs().size());
      if (settings.isOptionsAlwaysConfirm()) {
        return subSelection.getUserInput(release);
      } else if (release.getMatchingSubs().size() == 1
          && release.getMatchingSubs().get(0).getSubtitleMatchType() == SubtitleMatchType.EXACT) {
        Logger.instance.debug("determineWhatSubtitleDownload: Exact Match");
        return 0;
      } else if (release.getMatchingSubs().size() > 1) {
        Logger.instance.debug("determineWhatSubtitleDownload: Multiple subs detected");
        
        //Automatic selection
        List<Subtitle> shortlist = subSelection.getAutomaticSelection(release.getMatchingSubs());
        release.setMatchingSubs(shortlist);
        // automatic selection results in 1 result
        if (shortlist.size() == 1) return 0;
        // nothing match the minimum automatic selection value
        if (shortlist.size() == 0) return -1;

        // still more then 1 subtitle, let the user decide!
        if (subtitleSelectionDialog) {
          Logger.instance.debug("determineWhatSubtitleDownload: Select subtitle with dialog");
          return subSelection.getUserInput(release);
        } else {
          Logger.instance.log("Multiple subs detected for: " + release.getFilename()
              + " Unhandleable for CMD! switch to GUI"
              + " or use '--selection' as switch in de CMD");
        }
      } else if (release.getMatchingSubs().size() == 1) {
        Logger.instance.debug("determineWhatSubtitleDownload: only one sub taking it!!!!");
        return 0;
      }
    }
    Logger.instance.debug("determineWhatSubtitleDownload: No subs found for: "
        + release.getFilename());
    return -1;
  }

  public static String buildDisplayLine(Subtitle subtitle) {
    String hearingImpaired = "";
    if (subtitle.isHearingImpaired()) {
      hearingImpaired = " Hearing Impaired";
    }
    String uploader = "";
    if (!subtitle.getUploader().isEmpty())
      uploader = " (Uploader: " + subtitle.getUploader() + ") ";
    return "Scrore:" + subtitle.getScore() + "% " + subtitle.getFilename() + hearingImpaired
        + uploader + " (Source: " + subtitle.getSubtitleSource() + ") ";
  }

  public List<File> getFileListing(File dir, boolean recursieve, String languagecode,
                                 boolean forceSubtitleOverwrite) {
    /* Reset progress counters */
    this.progressFileIndex = 0;
    this.progressFilesTotal = 0;

    /* Start listing process */
    return this._getFileListing(dir, recursieve, languagecode, forceSubtitleOverwrite);
  }

  private List<File> _getFileListing(File dir, boolean recursieve, String languagecode,
      boolean forceSubtitleOverwrite) {
    Logger.instance.trace("Actions", "getFileListing", "dir: " + dir + " recursieve: " + recursieve
        + " languagecode: " + languagecode + " forceSubtitleOverwrite: " + forceSubtitleOverwrite);
    final List<File> filelist = new ArrayList<File>();
    final File[] contents = dir.listFiles();

    if(contents == null) return filelist;

    /* Increase progressTotalFiles count */
    this.progressFilesTotal += contents.length;

    for (final File file : contents) {
      /* Increase progressFileIndex */
      this.progressFileIndex++;

      /* Update progressListener */
      if (this.indexingProgressListener != null) {
        /* Tell the progresslistener which directory we are handling */
        this.indexingProgressListener.progress(dir.getPath());
        /* Tell the progresslistener the overall progress */
        int progress = (int) Math.floor((float) this.progressFileIndex / this.progressFilesTotal * 100);
        this.indexingProgressListener.progress(progress);
      }

      if (file.isFile()
        && isValidVideoFile(file)
        && (!fileHasSubtitles(file, languagecode) || forceSubtitleOverwrite)
        && isNotExcluded(file)) {
        filelist.add(file);
      } else if (file.isDirectory() && recursieve && !isExcludedDir(file)) {
        filelist.addAll(getFileListing(file, recursieve, languagecode, forceSubtitleOverwrite));
      }
    }
    return filelist;
  }

  private Boolean isExcludedDir(File file) {
    Boolean status = false;

    Iterator<SettingsExcludeItem> itemIterator = settings.getExcludeList().iterator();
    while (!status && itemIterator.hasNext()) {
      SettingsExcludeItem item = itemIterator.next();
      if (item.getType() != SettingsExcludeType.FOLDER) continue;

      File excludeFile = new File(item.getDescription());
      if (!excludeFile.equals(file)) continue;

      Logger.instance.trace("Actions", "getFileListing", "Skipping: " + file);
      status = true;
    }

    return status;
  }

  private boolean isNotExcluded(File file) {
    for (int j = 0; j < settings.getExcludeList().size(); j++) {
      if (settings.getExcludeList().get(j).getType() == SettingsExcludeType.REGEX) {
        NamedPattern np =
            NamedPattern.compile(
                settings.getExcludeList().get(j).getDescription().replace("*", ".*") + ".*$",
                Pattern.CASE_INSENSITIVE);
        NamedMatcher namedMatcher = np.matcher(file.getName());
        if (namedMatcher.find()) {
          Logger.instance.trace("Actions", "isNotExcluded", "Skipping: " + file);
          return false;
        }
      }
    }
    for (int j = 0; j < settings.getExcludeList().size(); j++) {
      if (settings.getExcludeList().get(j).getType() == SettingsExcludeType.FILE) {
        File excludeFile = new File(settings.getExcludeList().get(j).getDescription());
        if (excludeFile.equals(file)) {
          Logger.instance.trace("Actions", "isNotExcluded", "Skipping: " + file);
          return false;
        }
      }
    }
    return true;
  }

  public boolean isValidVideoFile(File file) {
    final String filename = file.getName();
    final int mid = filename.lastIndexOf(".");
    final String ext = filename.substring(mid + 1, filename.length());
    if (filename.contains("sample")) return false;
    for (String allowedExtension : VideoPatterns.EXTENSIONS) {
      if (ext.equalsIgnoreCase(allowedExtension)) return true;
    }
    return false;
  }

  /**
   * @param release
   * @param subtitle
   * @param librarySettings
   * @param version
   * @throws Exception
   */
  public static void download(Release release, Subtitle subtitle, LibrarySettings librarySettings,
      int version) throws Exception {
    Logger.instance.trace("Actions", "download",
        "LibraryAction" + librarySettings.getLibraryAction());
    PathLibraryBuilder pathLibraryBuilder = new PathLibraryBuilder(librarySettings);
    final File path = new File(pathLibraryBuilder.build(release));
    if (!path.exists()) {
      Logger.instance.debug("Download creating folder: " + path.getAbsolutePath());
      if (!path.mkdirs()) {
        throw new Exception("Download unable to create folder: " + path.getAbsolutePath());
      }
    }

    FilenameLibraryBuilder filenameLibraryBuilder = new FilenameLibraryBuilder(librarySettings);
    final String videoFileName = filenameLibraryBuilder.build(release);
    final String subFileName =
        filenameLibraryBuilder.buildSubtitle(release, subtitle, videoFileName, version);
    final File subFile = new File(path, subFileName);

    boolean success;

    if (HttpClient.isUrl(subtitle.getDownloadlink())) {
      success =
          HttpClient.getHttpClient().doDownloadFile(new URL(subtitle.getDownloadlink()), subFile);
      Logger.instance.debug("doDownload file was: " + success);
    } else {
      Files.copy(new File(subtitle.getDownloadlink()), subFile);
      success = true;
    }
    if (ReleaseParser.getQualityKeyword(release.getFilename()).split(" ").length > 1) {
      String dropBoxName = "";
      if (subtitle.getSubtitleSource() == SubtitleSource.LOCAL) {
        dropBoxName =
            PrivateRepoIndex.getFullFilename(
                FilenameLibraryBuilder.changeExtension(release.getFilename(), ".srt"), "?",
                subtitle.getSubtitleSource().toString());
      } else {
        dropBoxName =
            PrivateRepoIndex.getFullFilename(
                FilenameLibraryBuilder.changeExtension(release.getFilename(), ".srt"),
                subtitle.getUploader(), subtitle.getSubtitleSource().toString());
      }
      DropBoxClient.getDropBoxClient().put(subFile, dropBoxName, subtitle.getLanguagecode());
    }

    if (success) {
      if (!librarySettings.getLibraryAction().equals(LibraryActionType.NOTHING)) {
        final File oldLocationFile = new File(release.getPath(), release.getFilename());
        if (oldLocationFile.exists()) {
          final File newLocationFile = new File(path, videoFileName);
          Logger.instance.log("Moving/Renaming " + videoFileName + " to folder " + path.getPath()
              + " , this might take a while... ");
          Files.move(oldLocationFile, newLocationFile);
          if (!librarySettings.getLibraryOtherFileAction().equals(
              LibraryOtherFileActionType.NOTHING)) {
            cleanUpFiles(librarySettings, release, path, videoFileName);
          }
          if (librarySettings.isLibraryRemoveEmptyFolders()
              && release.getPath().listFiles().length == 0) {
            release.getPath().delete();
          }
        }
      }
      if (librarySettings.isLibraryBackupSubtitle()) {
        String langFolder = "";
        if (subtitle.getLanguagecode().equals("nl")) {
          langFolder = "Nederlands";
        } else {
          langFolder = "Engels";
        }
        File backupPath =
            new File(librarySettings.getLibraryBackupSubtitlePath() + File.separator + langFolder
                + File.separator);

        if (!backupPath.exists()) {
          if (!backupPath.mkdirs()) {
            throw new Exception("Download unable to create folder: " + backupPath.getAbsolutePath());
          }
        }

        if (librarySettings.isLibraryBackupUseWebsiteFileName()) {
          Files.copy(subFile, new File(backupPath, subtitle.getFilename()));
        } else {
          Files.copy(subFile, new File(backupPath, subFileName));
        }
      }
    }
  }

  public static void rename(LibrarySettings librarySettings, File f, Release release) {
    Logger.instance
        .trace("Actions", "rename", "LibraryAction" + librarySettings.getLibraryAction());
    String filename = "";
    if (librarySettings.getLibraryAction().equals(LibraryActionType.RENAME)
        || librarySettings.getLibraryAction().equals(LibraryActionType.MOVEANDRENAME)) {
      FilenameLibraryBuilder filenameLibraryBuilder = new FilenameLibraryBuilder(librarySettings);
      filename = filenameLibraryBuilder.build(release);
      if (release.getExtension().equals("srt")) {
        String languageCode = "";
        try {
          if (librarySettings.isLibraryIncludeLanguageCode()) {
            languageCode = DetectLanguage.execute(f);
          }
        } catch (final Exception e) {
          Logger.instance.error("Unable to detect language, leaving language code blank");
        }

        filename = filenameLibraryBuilder.buildSubtitle(release, filename, languageCode, 0);
      }
    } else {
      filename = f.getName();
    }
    Logger.instance.trace("Actions", "rename", "filename" + filename);

    PathLibraryBuilder pathLibraryBuilder = new PathLibraryBuilder(librarySettings);
    final File newDir = new File(pathLibraryBuilder.build(release));
    boolean status = true;
    if (!newDir.exists()) {
      Logger.instance.debug("Creating dir: " + newDir.getAbsolutePath());
      status = newDir.mkdirs();
    }

    Logger.instance.trace("Actions", "rename", "newDir" + newDir);

    if (status) {
      final File file = new File(release.getPath(), release.getFilename());

      try {

        if (librarySettings.getLibraryAction().equals(LibraryActionType.MOVE)
            || librarySettings.getLibraryAction().equals(LibraryActionType.MOVEANDRENAME)) {
          Logger.instance.log("Moving " + filename + " to the library folder " + newDir
              + " , this might take a while... ");
          Files.move(file, new File(newDir, filename));
        } else {
          Logger.instance.log("Moving " + filename + " to the library folder " + release.getPath()
              + " , this might take a while... ");
          Files.move(file, new File(release.getPath(), filename));
        }
        if (!librarySettings.getLibraryOtherFileAction().equals(LibraryOtherFileActionType.NOTHING)) {
          cleanUpFiles(librarySettings, release, newDir, filename);
        }
        if (librarySettings.isLibraryRemoveEmptyFolders()
            && release.getPath().listFiles().length == 0) {
          release.getPath().delete();
        }
      } catch (IOException e) {
        Logger.instance.error("Unsuccessfull in moving the file to the libary");
      }

    }
  }

  private static void cleanUpFiles(LibrarySettings librarySettings, Release release, File path,
      String videoFileName) throws IOException {
    Logger.instance.trace("Actions", "cleanUpFiles",
        "LibraryOtherFileAction" + librarySettings.getLibraryOtherFileAction());
    final List<String> fileFilters = new ArrayList<String>();
    fileFilters.add("nfo");
    fileFilters.add("jpg");
    fileFilters.add("sfv");
    fileFilters.add("srr");
    fileFilters.add("srs");
    fileFilters.add("nzb");
    fileFilters.add("torrent");
    fileFilters.add("txt");
    final String[] files =
        release.getPath().list(
            new FilenameExtensionFilter(fileFilters.toArray(new String[fileFilters.size()])));

    final List<String> folderFilters = new ArrayList<String>();
    folderFilters.add("sample");
    folderFilters.add("Sample");
    final String[] folders =
        release.getPath().list(
            new FilenameContainsFilter(folderFilters.toArray(new String[folderFilters.size()])));

    // remove duplicates using set
    final Set<String> list =
        new LinkedHashSet<String>(Arrays.asList(StringUtils.join(files, folders)));

    if (librarySettings.getLibraryOtherFileAction().equals(LibraryOtherFileActionType.REMOVE)) {
      for (String s : list) {
        final File file = new File(release.getPath(), s);
        if (file.isDirectory()) {
          FileUtils.deleteDirectory(file);
        } else {
          file.delete();
        }
      }
    } else if (librarySettings.getLibraryOtherFileAction().equals(LibraryOtherFileActionType.MOVE)) {
      for (String s : list) {
        Files.move(new File(release.getPath(), s), new File(path, s));
      }
    } else if (librarySettings.getLibraryOtherFileAction().equals(
        LibraryOtherFileActionType.MOVEANDRENAME)) {
      for (String s : list) {
        String extension = ReleaseParser.extractFileNameExtension(s);

        File f = new File(release.getPath(), s);

        if (s.contains("sample") && !f.isDirectory()) {
          extension = "sample." + extension;
        }

        if (f.isFile()) {
          final String filename =
              videoFileName.substring(0, videoFileName.lastIndexOf(".")).concat("." + extension);
          Files.move(f, new File(path, filename));
        } else {
          Files.move(f, new File(path, s));
        }
      }
    } else if (librarySettings.getLibraryOtherFileAction()
        .equals(LibraryOtherFileActionType.RENAME)) {
      for (String s : files) {
        String extension = ReleaseParser.extractFileNameExtension(s);

        File f = new File(release.getPath(), s);

        if (s.contains("sample") && !f.isDirectory()) {
          extension = "sample." + extension;
        }

        if (f.isFile()) {
          final String filename =
              videoFileName.substring(0, videoFileName.lastIndexOf(".")).concat("." + extension);
          Files.move(f, new File(release.getPath(), filename));
        } else {
          Files.move(f, new File(path, s));
        }
      }
    }
  }

  public boolean fileHasSubtitles(File file, String languageCode) {
    String subname = "";
    for (String allowedExtension : VideoPatterns.EXTENSIONS) {
      if (file.getName().contains("." + allowedExtension))
        subname = file.getName().replace("." + allowedExtension, ".srt");
    }

    final File f = new File(file.getParentFile(), subname);
    if (f.exists()) {
      return true;
    } else {
      List<String> filters = new ArrayList<String>();
      if (languageCode.equals("nl")) {
        filters.add("nld.srt");
        filters.add("ned.srt");
        filters.add("dutch.srt");
        filters.add("dut.srt");
        filters.add("nl.srt");
        if (!settings.getEpisodeLibrarySettings().getDefaultNlText().equals(""))
          filters.add("." + settings.getEpisodeLibrarySettings().getDefaultNlText().concat(".srt"));
        final String[] contents =
            file.getParentFile().list(
                new FilenameExtensionFilter(filters.toArray(new String[filters.size()])));
        return checkFileListContent(contents, subname.replace(".srt", ""));
      } else if (languageCode.equals("en")) {
        filters.add("eng.srt");
        filters.add("english.srt");
        filters.add("en.srt");
        if (!settings.getEpisodeLibrarySettings().getDefaultEnText().equals(""))
          filters.add("." + settings.getEpisodeLibrarySettings().getDefaultEnText().concat(".srt"));
        final String[] contents =
            file.getParentFile().list(
                new FilenameExtensionFilter(filters.toArray(new String[filters.size()])));
        return checkFileListContent(contents, subname.replace(".srt", ""));
      }
    }
    return false;
  }

  public boolean checkFileListContent(String[] contents, String subname) {
    if (contents.length > 0) {
      for (final String file : contents) {
        if (file.contains(subname)) {
          return true;
        }
      }
    }
    return false;
  }

  public void download(Release release, Subtitle subtitle, int version) throws Exception {
    if (release.getVideoType().equals(VideoType.EPISODE)) {
      download(release, subtitle, settings.getEpisodeLibrarySettings(), version);
    } else if (release.getVideoType().equals(VideoType.MOVIE)) {
      download(release, subtitle, settings.getMovieLibrarySettings(), version);
    }
  }

  public void download(Release release, Subtitle subtitle) throws Exception {
    Logger.instance.log("Downloading subtitle" + ": " + subtitle.getFilename() + " for release"
        + ": " + release.getFilename());
    download(release, subtitle, 0);
  }

  public void setIndexingProgressListener(IndexingProgressListener indexingProgressListener) {
    this.indexingProgressListener = indexingProgressListener;
  }
}
