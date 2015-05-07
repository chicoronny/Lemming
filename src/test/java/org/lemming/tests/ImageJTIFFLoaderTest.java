package org.lemming.tests;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.lemming.data.FastStore;
import org.lemming.inputs.ImageJTIFFLoader;
import org.lemming.interfaces.Frame;
import org.lemming.utils.LemMING;

/**
 * Test class for reading a TIF file.
 * 
 * @author Joe Borbely, Thomas Pengo
 */
@SuppressWarnings("rawtypes")
public class ImageJTIFFLoaderTest {

	Frame f;
	ImageJTIFFLoader tif;
	FastStore<Frame> frames;

	@SuppressWarnings("unchecked")
	@Before
	public void setUp() throws Exception {
		tif = new ImageJTIFFLoader("/Users/ronny/Documents/TubulinAF647.tif");

		frames = new FastStore<Frame>();
		
		tif.setOutput(frames);
	}

	@Test
	public void test() {
		long start = System.currentTimeMillis();
		tif.run();
		long end = System.currentTimeMillis();
		System.out.println("Time eleapsed: "+ (end-start));
		assertEquals(9990, frames.getLength());		
		
		tif.show();

		LemMING.pause(5000);
	}

}
