package net.gnehzr.tnoodle.test;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.SortedMap;

import net.gnehzr.tnoodle.scrambles.InvalidScrambleException;
import net.gnehzr.tnoodle.scrambles.ScrambleCacher;
import net.gnehzr.tnoodle.scrambles.ScrambleCacherListener;
import net.gnehzr.tnoodle.scrambles.Scrambler;
import net.gnehzr.tnoodle.utils.BadClassDescriptionException;
import net.gnehzr.tnoodle.utils.LazyClassLoader;

public class ScrambleTest {
	
	static class LockHolder extends Thread {
		private Object o;
		public void setObjectToLock(Object o) {
			synchronized(this) {
				this.o = o;
				if(isAlive()) {
					notify();
				} else {
					start();
				}
			}
			try {
				Thread.sleep(100); // give the locker thread a chance to grab the lock
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		@Override
		public synchronized void run() {
			while(o != null) {
				synchronized(o) {
					System.out.println("GOT LOCK " + o);
					Object locked = o;
					while(o == locked) {
						try {
							wait();
						} catch (InterruptedException e) {}
					}
				}
			}
		}
	}
	
	public static void main(String[] args) throws BadClassDescriptionException, IOException, IllegalArgumentException, SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException, InvalidScrambleException {
		LockHolder lh = new LockHolder();

		int SCRAMBLE_COUNT = 1000;
		SortedMap<String, LazyClassLoader<Scrambler>> lazyScramblers = Scrambler.getScramblers();
//		for(String puzzle : lazyScramblers.keySet()) {
		for(String puzzle : new String[]{"2x2x2"}) {
			LazyClassLoader<Scrambler> lazyScrambler = lazyScramblers.get(puzzle);
			final Scrambler scrambler = lazyScrambler.cachedInstance();
			
			// Generating a scramble
			System.out.println("Generating a " + puzzle + " scramble");
			String scramble;
			lh.setObjectToLock(scrambler);
			scramble = scrambler.generateScramble();
			
			// Drawing that scramble
			System.out.println("Drawing " + scramble);
			BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
			Dimension size = new Dimension(image.getWidth(), image.getHeight());
			scrambler.drawScramble(image.createGraphics(), size, scramble, null);
			
			// TODO - actually draw the scramble as well!
			System.out.println("Generating & drawing 2 sets of " + SCRAMBLE_COUNT + " scrambles simultaneously." +
								" This is meant to shake out threading problems in scramblers");
			final ScrambleCacher c1 = new ScrambleCacher(scrambler, 1000);
			final ScrambleCacher c2 = new ScrambleCacher(scrambler, 1000);
			ScrambleCacherListener cacherStopper = new ScrambleCacherListener() {
				@Override
				public void scrambleCacheUpdated(ScrambleCacher src) {
					if(src.getAvailableCount() == src.getCacheSize()) {
						src.stop();
						synchronized(c1) {
							c1.notify();
						}
					}
				}
			};
			c1.addScrambleCacherListener(cacherStopper);
			c2.addScrambleCacherListener(cacherStopper);
			while(c1.isRunning() || c2.isRunning()) {
				synchronized(c1) {
					try {
						c1.wait();
					} catch(InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		
		}
		lh.setObjectToLock(null);
		System.out.println("DONE");
	}
}
