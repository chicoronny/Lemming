package org.lemming.tests;

import java.io.File;

import net.imglib2.util.Util;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.lemming.data.FastStore;
import org.lemming.data.Pipeline;
import org.lemming.inputs.ImageJTIFFLoader;
import org.lemming.interfaces.Frame;
import org.lemming.interfaces.Localization;
import org.lemming.outputs.PrintToFile;
import org.lemming.processors.DogDetector;

@SuppressWarnings({"javadoc","rawtypes"})
public class DogDetectorTest {
	
	private ImageJTIFFLoader tif;
	private DogDetector detector;
	private double[] calibration;
	private FastStore<Frame> frames;
	private FastStore<Localization> localizations;
	private PrintToFile print;
	private Pipeline pipe;
	

	@SuppressWarnings({ "unchecked" })
	@Before
	public void setUp() throws Exception {
		pipe = new Pipeline();
		frames = new FastStore<Frame>();
		tif = new ImageJTIFFLoader("/home/ronny/Bilder/TubulinAF647.tif");
		tif.setOutput(frames);
		pipe.add(tif);
		
		localizations = new FastStore<Localization>();
		calibration = Util.getArrayFromValue( 1d, 2 );
		detector = new DogDetector(5,calibration,20);
		detector.setInput(frames);
		detector.setOutput(localizations);
		pipe.add(detector);
		
		File f = new File("/home/ronny/Bilder/resultsDogDetector.csv");
		print = new PrintToFile(f);
		print.setInput(localizations);	
		pipe.add(print);
	}
		

	@Test
	public void test() {
		long startTime = System.currentTimeMillis();
		pipe.run();
		Thread t_load = new Thread(tif,"ImageJTIFFLoader");
		Thread t_detector = new Thread(detector,"DogDetector");
		Thread t_print = new Thread(print,"PrintToFile");
		
		t_load.start();
		/*if (frames.isEmpty())
			try {
				Thread.sleep(10);
				System.out.println("delay of 10 ms");
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}*/
		t_detector.start();
		t_print.start();
		
		try {
			t_load.join();
			t_detector.join();
			t_print.join();
		} catch (InterruptedException e) {
			System.out.println(e.getMessage());
		}
		long endTime = System.currentTimeMillis();
		System.out.println(endTime-startTime);
		assertEquals(true,frames.isEmpty());
	}


}
