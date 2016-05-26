package org.lemming.tests;

import static org.junit.Assert.*;

import java.io.File;
import java.util.concurrent.Executors;

import org.junit.Before;
import org.junit.Test;
import org.lemming.modules.Renderer;
import org.lemming.modules.StoreLoader;
import org.lemming.pipeline.Manager;
import org.lemming.plugins.HistogramRenderer;

public class HistogramRendererTest {

	private Manager pipe;
	private Renderer histo;

	@Before
	public void setUp() {
		pipe = new Manager(Executors.newCachedThreadPool());
		
		//StoreLoader reader = new StoreLoader(new File("H:/Images/activations_2D.csv"), ",");
		StoreLoader reader = new StoreLoader(new File("H:/Images/sequence-as-stack-MT2.N1-2D-Exp-MLE.csv"), "\t");
		pipe.add(reader);
		
		histo = new HistogramRenderer(1024, 1024, 0, 6400, 0, 6400, 0, 500);
		pipe.add(histo);
		pipe.linkModules(reader, histo, true, 200);
	}

	@Test
	public void test() {
		pipe.run();
		histo.show();
		assertEquals(true,pipe.getMap().values().iterator().next().isEmpty());
	}

}
