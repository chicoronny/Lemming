package org.lemming.tests;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Before;
import org.junit.Test;
import org.lemming.data.FastStore;
import org.lemming.data.Pipeline;
import org.lemming.inputs.ImageJTIFFLoader;
import org.lemming.interfaces.Frame;
import org.lemming.interfaces.Localization;
import org.lemming.interfaces.Store;
import org.lemming.outputs.PrintToFile;
import org.lemming.processors.PeakFinder;

/**
 * Test class for finding peaks based on a threshold value and inserts the
 * the frame number and the x,y coordinates of the localization into a Store.
 * 
 * @author Joe Borbely, Thomas Pengo
 */
@SuppressWarnings({"javadoc","rawtypes"})
public class PeakFinderTest {

	private ImageJTIFFLoader tif;
	private Store<Frame> frames;
	private Store<Localization> localizations;
	private PeakFinder peak;
	private PrintToFile print;
	private Pipeline pipe;

	
	@SuppressWarnings("unchecked")
	@Before
	public void setUp() throws Exception {
		pipe = new Pipeline();	
		
		frames = new FastStore<Frame>();
		tif = new ImageJTIFFLoader("/home/ronny/Bilder/DRG.tif");
		tif.setOutput(frames);
		pipe.props.setProperty("Stack Size", tif.getSize().toString());
		pipe.add(tif);
		
		localizations = new FastStore<Localization>();
		peak = new PeakFinder(700,4);
		peak.setInput(frames);
		peak.setOutput(localizations);
		pipe.add(peak);
		
		File f = new File("/home/ronny/Bilder/resultsPeakFinder.csv");
		print = new PrintToFile(f);
		print.setInput(localizations);
		pipe.add(print);		
	}

	@Test
	public void test() {
		long startTime = System.currentTimeMillis();
		pipe.run();
		long endTime = System.currentTimeMillis();
		System.out.println(endTime-startTime);		
		assertEquals(true,frames.isEmpty());
	}

}
