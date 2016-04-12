package org.lemming.factories;

import java.util.Map;

import org.lemming.gui.ConfigurationPanel;
import org.lemming.interfaces.Detector;
import org.lemming.interfaces.PluginInterface;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * Factory for detectors
 * 
 * @author Ronny Sczech
 *
 */
public interface DetectorFactory extends PluginInterface{

	/**
	 * Check that the given settings map is suitable for target detector.
	 *
	 * @param settings 
	 * the map to test.
	 * @return <code>true</code> if the settings map is valid.
	 */
	public boolean setAndCheckSettings( final Map< String, Object > settings );
	
	/**
	 *  @param <T>
	 * @return  Module to process
	 */
	public <T extends RealType<T> & NativeType<T>> Detector<T> getDetector();
	
	/**
	 * Returns a new GUI panel able to configure the settings suitable for this
	 * specific detector factory.
	 */
	public ConfigurationPanel getConfigurationPanel();
	
	public boolean hasPreProcessing();
	
}
