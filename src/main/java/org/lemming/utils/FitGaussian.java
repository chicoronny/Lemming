package org.lemming.utils;

import java.util.Arrays;

import Jama.Matrix;

public class FitGaussian {
	
	 /**Location in xSeries where Gaussian data starts */
    private int dataStart;
    
    /**Location in xSeries where Gaussian data ends */
    private int dataEnd;
    
    /**Amplitude parameter*/
    private double amp;
    
    /**Center parameter*/
    private double xInit;
    
    /**Sigma parameter*/
    private double sigma;
    
    /**R squared*/
    private double rSquared;
    
    /**Max number of iterations to perform. */
	public static final int MAX_ITR = 50;
	
	/**Min number of iterations to perform. */
	public static final int MIN_ITR = 5;
	
	/**Minimum allowable distance between iterations of a coefficient before considered converged. */
	public static final double EPSILON = .005;
    
    private int iters;

	private double[] xSeries;

	private double[] ySeries;

	private double[] a;
	
	/**Total error as X^2 */
	protected double chisq;
    
    /**Fitted y-data, based on original x points.*/
    protected double[] yDataFitted;
    
    public FitGaussian(int nPoints, double[] xData, double[] yData) {

        // nPoints data points, 3 coefficients, and exponential fitting
    	a = new double[3];
        
        this.xSeries = xData;
        this.ySeries = yData;
        
        //bounds = 0; // bounds = 0 means unconstrained
        // bounds = 1 means same lower and upper bounds for
        // all parameters
        // bounds = 2 means different lower and upper bounds
        // for all parameters
        
        estimateInitial();        
    }

	private void estimateInitial() {
		int offset = 15;
    	
    	//determine location of start data, note 
    	//basic thresholding will already have been performed
    	dataStart = 0;
    	for(int i=0; i<ySeries.length; i++) {
    		if(ySeries[i] != 0 && i > 0) {
    			dataStart = i > offset ? i-offset : 0;
    			break;
    		}		
    	}
    	
    	//estimate xInit
    	int maxIndex = 0;
    	double totalDataCount = 0;
    	for(int i=dataStart; i<ySeries.length; i++) {
    		if(ySeries[i] > ySeries[maxIndex]) {
    			maxIndex = i;
    		}
    		if(ySeries[i] > 0) {
    			totalDataCount += ySeries[i];
    		}
    	}	
    	xInit = xSeries[maxIndex];
    	
    	//determine location of end data
    	dataEnd = 0;
    	for(int i=maxIndex; i<ySeries.length; i++) {
    		if(ySeries[i] == 0) {
    			dataEnd = i+offset < ySeries.length-1 ? i+offset : ySeries.length-1;
    			break;
    		}
    	}

    	//find location of one sigma data collection point
    	double dataCollectedOneSigma = ySeries[maxIndex], dataCollectedTwoSigma = ySeries[maxIndex];
    	int xStopLeftIndex = maxIndex, xStopRightIndex = maxIndex;
    	boolean left = true;
    	while(dataCollectedOneSigma / totalDataCount < .68 && 
    			xStopLeftIndex > dataStart+1 && xStopRightIndex < dataEnd-1) {
    		if(left) 
    			dataCollectedOneSigma += ySeries[--xStopLeftIndex];
    		if(!left)
    			dataCollectedOneSigma += ySeries[++xStopRightIndex];
    		left = !left;
    	}
    	
    	//estimate one sigma from stopping locations
    	double oneSigmaEstimate = 0;
    	if(dataCollectedOneSigma / totalDataCount >= .68) {
    		double sigmaLeft = Math.abs(xSeries[maxIndex] - xSeries[xStopLeftIndex]);
    		double sigmaRight = Math.abs(xSeries[maxIndex] - xSeries[xStopLeftIndex]);
    		oneSigmaEstimate = sigmaLeft + sigmaRight / 2.0;
    	}
    	
    	//find location of two sigma data collection point
    	dataCollectedTwoSigma = dataCollectedOneSigma;
    	while(dataCollectedTwoSigma / totalDataCount < .95 && 
    			xStopLeftIndex > dataStart+1 && xStopRightIndex < dataEnd-1) {
    		if(left) 
    			dataCollectedTwoSigma += ySeries[--xStopLeftIndex];
    		if(!left)
    			dataCollectedTwoSigma += ySeries[++xStopRightIndex];
    		left = !left;
    	}
    	
    	//estimate two sigma from stopping location
    	double twoSigmaEstimate = 0;
    	if(dataCollectedOneSigma / totalDataCount >= .68) {
    		double sigmaLeft = Math.abs(xSeries[maxIndex] - xSeries[xStopLeftIndex]);
    		double sigmaRight = Math.abs(xSeries[maxIndex] - xSeries[xStopLeftIndex]);
    		twoSigmaEstimate = sigmaLeft + sigmaRight / 2.0;
    	}
    	
    	//use both measurements to estimate stdev
    	if(twoSigmaEstimate != 0)
    		sigma = (oneSigmaEstimate + .5*twoSigmaEstimate) / 2;
    	else 
    		sigma = oneSigmaEstimate;
    	
    	//estimate for amplitude
    	amp = ySeries[maxIndex];
    	
    	a[0] = amp;
    	a[1] = xInit;
    	a[2] = sigma;
	}
	
	public double[] getFittedY() {
    	if(yDataFitted == null)
    		calculateFittedY();
    	return yDataFitted;
    }
	
	protected void calculateFittedY() {
		 yDataFitted = new double[xSeries.length];
		 for(int i=0; i<xSeries.length; i++) {
			 yDataFitted[i] = gauss(xSeries[i]);
		 }
	}
	
	protected double getMedian(double[] toSort) {
	    int length = toSort.length;
	
	    Arrays.sort(toSort);
	
	    return toSort[(length / 2)];
	}
	
	  /**
     * Gaussian evaluated at a point with given parameters
     */
    private double gauss(double x) {
    	double exp = -Math.pow(x-xInit, 2) / (2 * Math.pow(sigma, 2));
    	
    	double f = amp*Math.exp(exp);
    	
    	return f;
    }
    
    /**
     * Partial derivative of gaussian with respect to A.
     */
    private double dgdA(double x) {
    	double exp = -Math.pow(x-xInit, 2) / (2 * Math.pow(sigma, 2));
    	
    	double f = Math.exp(exp);
    	
    	return f;
    	
    }
    
    /**
     * Partial derivative of gaussian with respect to x.
     */
    private double dgdx(double x) {
    	double exp = -Math.pow(x-xInit, 2) / (2 * Math.pow(sigma, 2));
    	
    	double coeff = (amp * (x-xInit))/(Math.pow(sigma, 2));
    	
    	double f = coeff*Math.exp(exp);
    	
    	return f;
    }
    
    /**
     * Partial derivative of gaussian with respect to sigma.
     */
    private double dgdsigma(double x) {
    	double exp = -Math.pow(x-xInit, 2) / (2 * Math.pow(sigma, 2));
    	
    	double coeff = (amp * Math.pow(x-xInit, 2))/(Math.pow(sigma, 3));
    	
    	double f = coeff*Math.exp(exp);
    	
    	return f;
    }
    
    /**
     * Jacobian used for non-linear least squares fitting.
     */
    protected Matrix generateJacobian() {
    	Matrix jacobian = new Matrix(dataEnd - dataStart, 3);
    	for(int i=dataStart; i<dataEnd; i++) {
    		jacobian.set(i-dataStart, 0, dgdA(xSeries[i]));
    		jacobian.set(i-dataStart, 1, dgdx(xSeries[i]));
    		jacobian.set(i-dataStart, 2, dgdsigma(xSeries[i]));
    	}
    	
    	return jacobian;
    }
    
    protected Matrix generateResiduals() {
    	Matrix residuals = new Matrix(dataEnd - dataStart, 1);
    	for(int i=dataStart; i<dataEnd; i++) {
    		double r = ySeries[i] - gauss(xSeries[i]);
    		residuals.set(i-dataStart, 0, r);
    	}
    	
    	return residuals;
    }
    
    protected void calculateChiSq() {
    	Matrix residuals = generateResiduals();
    	double sum = 0;
    	double resSum = 0;
    	for(int i=dataStart; i<dataEnd; i++) {
    		double resTemp = residuals.get(i-dataStart, 0);
    		resSum += Math.abs(resTemp);
    		if(gauss(xSeries[i]) > .01)
    			residuals.set(i-dataStart, 0, Math.pow(resTemp, 2)/gauss(xSeries[i]));
    		else
    			residuals.set(i-dataStart, 0, 0);
    		System.out.println("xValue: "+xSeries[i]+"\tActual: "+ySeries[i]+"\tExpected: "+gauss(xSeries[i])+"\tResidual: "+resTemp+"\tChi squared value: "+residuals.get(i-dataStart, 0));
    		sum += Math.pow(resTemp, 2)/gauss(xSeries[i]);
    	}
    	
    	chisq = residuals.norm1();
    	System.out.println("Sum "+sum+"\tcompared to chisq "+chisq);
    	rSquared = 1.0-(chisq/resSum);
    	System.out.println("Residual sum: "+resSum+"\tChisquared: "+chisq+"\trSquared: "+rSquared);
    	
    }
    
public void driver() {
        
    	boolean converged = false;
    	iters = 0;
    	
    	System.out.println("Initial guess:\tAmp: "+amp+"\txInit: "+xInit+"\tSigma: "+sigma);
    	
    	while(!converged && iters < MAX_ITR) {
    		double oldAmp = amp;
        	double oldXInit = xInit;
        	double oldSigma = sigma;
    	
	    	Matrix jacobian = generateJacobian();
	    	Matrix residuals = generateResiduals();
	    	
	    	Matrix lhs = jacobian.transpose().times(jacobian);
	    	Matrix rhs = jacobian.transpose().times(residuals);
	    	
	    	Matrix dLambda = lhs.solve(rhs);
	    	
	    	amp = amp + dLambda.get(0, 0);
	    	xInit = xInit + dLambda.get(1, 0);
	    	sigma = sigma + dLambda.get(2, 0);
	    	
	    	System.out.println("Iteration "+iters+"\tAmp: "+amp+"\txInit: "+xInit+"\tSigma: "+sigma);
	    	
	    	if(Math.abs(Math.abs(oldAmp - amp) / ((oldAmp + amp) / 2)) < EPSILON && 
	    			Math.abs(Math.abs(oldXInit - xInit) / ((oldXInit + xInit) / 2)) < EPSILON && 
	    			Math.abs(Math.abs(oldSigma - sigma) / ((oldSigma + sigma) / 2)) < EPSILON && iters > MIN_ITR) {
	    		converged = true;    		
	    		System.out.println("Converged after "+iters+" iterations.");
	    	} else {
	    		oldAmp = amp;
	    		oldXInit = xInit;
	    		oldSigma = sigma;
	    		iters++;
	    	}
    	}
    	
    	if(!converged) {
    		System.out.println("Did not converge after "+iters+" iterations.");
    	} else {
    		calculateFittedY();
    		calculateChiSq();
    	}
    	
    	//a already initialized, used to hold parameters for output
    	a[0] = amp;
    	a[1] = xInit;
    	a[2] = sigma;
    }

}
