package org.lemming.plugins;

import java.util.ArrayList;
import java.util.List;
import org.lemming.interfaces.Detector;
import org.lemming.interfaces.Element;
import org.lemming.interfaces.Frame;
import org.lemming.pipeline.FrameElements;
import org.lemming.pipeline.Localization;
import org.lemming.pipeline.MultiRunModule;
import net.imglib2.type.numeric.RealType;

public class SetPeak<T extends RealType<T>> extends MultiRunModule implements Detector<T>{

	private final Localization peak;

	/**
	 * @param threshold
	 *            - threshold for subtracting background
	 * @param size
	 *            - kernel size
	 * @param gaussian - gaussian size (0=omit)
	 */
	public SetPeak(final Localization peak) {
		this.peak = peak;
		Thread.currentThread().setName(this.getClass().getSimpleName());
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Element processData(Element data) {
		Frame<T> frame = (Frame<T>) data;
		if (frame == null)
			return null;

		if (frame.isLast()) { // make the poison pill
			cancel();
			FrameElements<T> res = detect(frame);
			if (res!=null){
				res.setLast(true);
				counterList.add(res.getList().size());
				return res;
			} else {
				res = new FrameElements<>(null, frame);
				res.setLast(true);
				return res;
			}
		}
		FrameElements<T> res = detect(frame);
		if (res != null)
			counterList.add(res.getList().size());
		return res;
	}

	@Override
	public FrameElements<T> detect(final Frame<T> frame) {
		List<Element> found = new ArrayList<>();
		found.add(new Localization(peak.getX(),peak.getY(),peak.getIntensity(), frame.getFrameNumber()));
		return new FrameElements<>(found, frame);
	}

	
	@Override
	protected void afterRun() {
		Integer cc=0;
		for (Integer i : counterList)
			cc+=i;
		System.out.println("Detector found "
				+ cc + " peaks in "
				+ (System.currentTimeMillis() - start) + "ms.");
	}

	@Override
	public boolean check() {
		return inputs.size()==1 && outputs.size()>=1;
	}
}
