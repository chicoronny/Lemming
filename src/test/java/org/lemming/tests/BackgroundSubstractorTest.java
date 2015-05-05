package org.lemming.tests;

import net.imglib2.img.display.imagej.ImageJFunctions;

import org.junit.Before;
import org.junit.Test;
import org.lemming.data.FastStore;
import org.lemming.data.Pipeline;
import org.lemming.inputs.ImageJTIFFLoader;
import org.lemming.interfaces.Frame;
import org.lemming.processors.BackgroundSubstractor;

@SuppressWarnings("rawtypes")
public class BackgroundSubstractorTest {

	
	private FastStore<Frame> frames;
	private Pipeline pipe;
	private ImageJTIFFLoader tif;
	private BackgroundSubstractor bs;
	private FastStore<Frame> substractedFrames;

	@SuppressWarnings({ "unchecked" })
	@Before
	public void setUp() throws Exception {
		pipe = new Pipeline();
		tif = new ImageJTIFFLoader("/home/ronny/Bilder/TubulinAF647.tif");

		frames = new FastStore<Frame>();
		tif.setOutput(frames);
		pipe.add(tif);
		
		bs = new BackgroundSubstractor(10,false,false);
		bs.setInput(frames);
		substractedFrames = new FastStore<Frame>();
		bs.setOutput(substractedFrames);
		pipe.add(bs);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void test() {
		pipe.run();
		ImageJFunctions.show( substractedFrames.get().getPixels(), "BackgroundSubstractor" );
	}

}
