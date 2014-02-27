package com.miloshpetrov.sol2.game.sound;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.miloshpetrov.sol2.SolFiles;
import com.miloshpetrov.sol2.common.Nullable;
import com.miloshpetrov.sol2.common.SolMath;
import com.miloshpetrov.sol2.game.*;
import com.miloshpetrov.sol2.game.planet.Planet;
import com.miloshpetrov.sol2.menu.IniReader;
import com.miloshpetrov.sol2.ui.DebugCollector;

import java.util.*;

public class SoundMan {
  public static final String DIR = "res/sounds/";
  public static final float MAX_SPACE_DIST = 1f;
  private static final float MAX_VOL_RADIUS = 2;

  private final HashMap<String, SolSound> mySounds;
  private final DebugHintDrawer myHintDrawer;
  private final Map<SolObj, Map<SolSound, Float>> myLoopedSounds;

  public SoundMan() {
    mySounds = new HashMap<String, SolSound>();
    myHintDrawer = new DebugHintDrawer();
    myLoopedSounds = new HashMap<SolObj, Map<SolSound, Float>>();
  }

  public SolSound getLoopedSound(String relPath, @Nullable FileHandle configFile) {
    return getSound0(relPath, configFile, true);
  }

  public SolSound getSound(String relPath, @Nullable FileHandle configFile) {
    return getSound0(relPath, configFile, false);
  }

  public SolSound getSound0(String relPath, @Nullable FileHandle configFile, boolean looped) {
    SolSound res = mySounds.get(relPath);
    if (res != null) return res;

    String definedBy = configFile == null ? "hardcoded" : configFile.path();
    String dirPath = DIR + relPath;
    String paramsPath = dirPath + "/params.txt";
    FileHandle dir = SolFiles.readOnly(dirPath);
    float[] sp = loadSoundParams(paramsPath);
    float loopTime = sp[1];
    float volume = sp[0];
    res = new SolSound(dir.toString(), definedBy, loopTime, volume);
    mySounds.put(relPath, res);
    fillSounds(res.sounds, dir);
    boolean empty = res.sounds.isEmpty();
    if (!empty && looped && loopTime == 0) throw new AssertionError("please specify loopTime value in " + paramsPath);
    if (empty) {
      String warnMsg = "found no sounds in " + dir;
      if (configFile != null) {
        warnMsg += " (defined in " + configFile.path() + ")";
      }
      DebugCollector.warn(warnMsg);
    }
    return res;
  }

  private float[] loadSoundParams(String paramsPath) {
    float[] r = {0, 0};
    IniReader reader = new IniReader(paramsPath);
    r[0] = reader.f("volume", 1);
    r[1] = reader.f("loopTime", 0);
    return r;
  }

  private void fillSounds(List<Sound> list, FileHandle dir) {
    //try empty dirs
    //if (!dir.isDirectory()) throw new AssertionError("Can't load sound: can't find directory " + dir);
    for (FileHandle soundFile : dir.list()) {
      String ext = soundFile.extension();
      if (ext.equals("wav") || ext.equals("mp3") || ext.equals("ogg")) //filter by supported audio files
      {
        Sound sound = Gdx.audio.newSound(soundFile);
        list.add(sound);
      }
    }
  }

  /**
   * Plays a sound. Either pos or source must not be null.
   * @param pos position of a sound. If null, source.getPos() will be used
   * @param source bearer of a sound. Must not be null for looped sounds
   */
  public void play(SolGame game, SolSound sound, @Nullable Vector2 pos, @Nullable SolObj source) {
    if (DebugAspects.NO_SOUND) return;
    float time = game.getTime();

    if (pos == null) {
      if (source == null) return;
      pos = source.getPos();
    }
    if (source == null && sound.loopTime > 0) throw new AssertionError("looped sound without source object: " + sound.dir);

    Planet np = game.getPlanetMan().getNearestPlanet();
    Vector2 camPos = game.getCam().getPos();
    boolean atm = camPos.dst(np.getPos()) < np.getFullHeight();
    float dst = pos.dst(camPos);
    float vol = 1;
    if (MAX_VOL_RADIUS < dst) {
      vol /= dst;
    }
    float pitch = SolMath.rnd(.95f, 1.05f);
    if (!atm && !DebugAspects.SOUND_IN_SPACE) {
      vol = 1 - SolMath.clamp(dst / MAX_SPACE_DIST, 0, 1);
      pitch = .75f * vol + .25f;
    }
    vol *= sound.volume;
    if (vol <= 0) return;

    if (skipLooped(source, sound, time)) return;
    if (DebugAspects.SOUND_DEBUG) {
      myHintDrawer.add(source, pos, sound.getDebugString());
    }
    if (sound.sounds.isEmpty()) return;
    Sound sound0 = SolMath.elemRnd(sound.sounds);
    sound0.play(vol, pitch, 0);
  }

  private boolean skipLooped(SolObj source, SolSound sound, float time) {
    if (sound.loopTime == 0) return false;
    boolean playing = true;
    Map<SolSound, Float> looped = myLoopedSounds.get(source);
    if (looped == null) {
      looped = new HashMap<SolSound, Float>();
      myLoopedSounds.put(source, looped);
      playing = false;
    } else {
      Float endTime = looped.get(sound);
      if (endTime == null || endTime <= time) {
        looped.put(sound, time + sound.loopTime); // argh, performance loss
        playing = false;
      } else {
        playing = time < endTime;
      }
    }
    return playing;
  }

  public void drawDebug(Drawer drawer, SolGame game) {
    if (DebugAspects.SOUND_DEBUG) myHintDrawer.draw(drawer, game);
  }

  public void update(SolGame game) {
    if (DebugAspects.SOUND_DEBUG) myHintDrawer.update(game);
    cleanLooped(game);
  }

  private void cleanLooped(SolGame game) {
    Iterator<SolObj> it = myLoopedSounds.keySet().iterator();
    while (it.hasNext()) {
      SolObj o = it.next();
      if (o.shouldBeRemoved(game)) it.remove();
    }
  }

}
