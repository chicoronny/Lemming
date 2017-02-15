#define qrRankingThreshold FLT_EPSILON
#define initialStepBoundFactor 100
#define orthoTolerance 10e-10f
#define costRelativeTolerance 10e-10f
#define parRelativeTolerance 10e-10f
#define X0 0
#define Y0 1
#define SX 2
#define SY 3
#define I0 4
#define BG 5
#define sqrtPI 1.772453851f
#define CUDART_SQRT_TWO_F       1.414213562f
#define FLT_EPSILON      1.192092896e-07F
#define PARAM_LENGTH 6
#define IMSZBIG 21	


__device__ float toData(const float* m, const int r, const int c) {
	return m[r*PARAM_LENGTH + c];
}

__device__ void fromData(float* m, const int r, const int c, const float value) {
	if (m == NULL) return;
	m[r*PARAM_LENGTH + c] = value;
}

__device__ float dotProduct(float* v, float* w, const int dim) {
	float dot = 0.f;
	int i;

	for (i = 0; i < dim; i++)
		dot += v[i] * w[i];

	return dot;
}

__device__ void subtract(const float* v, const float* w, const int dim, float* result) {
	if (v == NULL || w == NULL) return;
	for (int i = 0; i < dim; i++)
		result[i] = v[i] - w[i];
}

__device__ float getCost(float* residuals, const int dim) {
	if (residuals == NULL) return NULL;
	return sqrtf(dotProduct(residuals, residuals, dim));
}

// Math functions

__device__ float dErf(const float v) {
	return 2.f * expf(-v*v) / sqrtPI;
}

__device__ float Ex(const int x, float* variables) {
	float tsx = 1.f / (CUDART_SQRT_TWO_F*variables[SX]);
	float e1 = erff(tsx*(x - variables[X0] + 0.5f));
	float e2 = erff(tsx*(x - variables[X0] - 0.5f));
	return 0.5f*e1 - 0.5f*e2;
}

__device__ float Ey(const int y, float* variables) {
	float tsy = 1.f / (CUDART_SQRT_TWO_F*variables[SY]);
	float e1 = erff(tsy*(y - variables[Y0] + 0.5f));
	float e2 = erff(tsy*(y - variables[Y0] - 0.5f));
	return 0.5f*e1 - 0.5f*e2;
}

__device__ float dEx(const int x, float* variables) {
	float tsx = 1.f / (CUDART_SQRT_TWO_F*variables[SX]);
	return 0.5f*tsx*(dErf(tsx*(x - variables[X0] - 0.5f)) - dErf(tsx*(x - variables[X0] + 0.5f)));
}

__device__ float dEy(const int y, float* variables) {
	float tsy = 1.f / (CUDART_SQRT_TWO_F*variables[SY]);
	return 0.5f*tsy*(dErf(tsy*(y - variables[Y0] - 0.5f)) - dErf(tsy*(y - variables[Y0] + 0.5f)));
}

__device__ float dEsx(const int x, float* variables) {
	float tsx = 1.f / (CUDART_SQRT_TWO_F*variables[SX]);
	return 0.5f*tsx*((x - variables[X0] - 0.5f)*dErf(tsx*(x - variables[X0] - 0.5f)) - (x - variables[X0] + 0.5f)*dErf(tsx*(x - variables[X0] + 0.5f))) / variables[SX];
}

__device__ float dEsy(const int y, float* variables) {
	float tsy = 1.f / (CUDART_SQRT_TWO_F*variables[SY]);
	return 0.5f*tsy*((y - variables[Y0] - 0.5f)*dErf(tsy*(y - variables[Y0] - 0.5f)) - (y - variables[Y0] + 0.5f)*dErf(tsy*(y - variables[Y0] + 0.5f))) / variables[SY];
}

__device__ float getValue(float* params, const int x, const int y) {
	float ex = Ex(x, params);
	float ey = Ey(y, params);
	float ret = params[I0] * ex * ey + params[BG];
	return ret;
}

__device__ void getValues(float* point, const int size, float* retVal) {
	int length = size*size;

	for (int i = 0; i < length; i++) {
		retVal[i] = getValue(point, i%size, i/size);
	}
}

__device__ void getJacobian(float* point, const int size, float* jacobian) {
	int length = size*size;
	
	for (int i = 0; i < length; ++i) {
		int x = i % size;
		int y = i / size;
		float ex = Ex(x, point);
		float ey = Ey(y, point);
		fromData(jacobian, i, X0, point[I0] * ey*dEx(x, point));
		fromData(jacobian, i, Y0, point[I0] * ex*dEy(y, point));
		fromData(jacobian, i, SX, point[I0] * ey*dEsx(x, point));
		fromData(jacobian, i, SY, point[I0] * ex*dEsy(y, point));
		fromData(jacobian, i, I0, ex*ey);
		fromData(jacobian, i, BG, 1.f);
	}
}

__device__	int converged(const int i, float* p, float* c) {
	if (i>200) return 0;
	if (abs(p[X0] - c[X0]) > 0.001)return 1;
	if (abs(p[Y0] - c[Y0]) > 0.001)return 2;
	if (abs(p[SX] - c[SX]) > 0.002)return 3;
	if (abs(p[SY] - c[SY]) > 0.002)return 4;	
	if (abs(p[I0] - c[I0]) > 0.01)return 5;
	if (abs(p[BG] - c[BG]) > 0.01)return 6;
	return 0;
}

// fitting functions

__device__ void qTy(const float* beta, const int* permutation, const float* wJ, const int nR, float* y) {
	
	for (int k = 0; k < PARAM_LENGTH; ++k) {
		int pk = permutation[k];
		float gamma = 0;
		for (int i = k; i < nR; ++i) {
			gamma += toData(wJ,i,pk) * y[i];
		}
		gamma *= beta[pk];
		for (int i = k; i < nR; ++i) {
			y[i] -= gamma * toData(wJ,i,pk);
		}
	}
}

__device__ float* qrDecomposition(const int solvedCols, float* diagR, float* jacNorm, float* beta, int* permutation, int* rank, const float* jacobian, const int nR, float* wjacobian) {
	const int nC = PARAM_LENGTH;
	float akk,norm2,ak2,aki,gamma,alpha,betak;
	int i,j,k,dk,pk,nextColumn;
		
	// Code in this class assumes that the weighted Jacobian is -(W^(1/2) J),
	// hence the multiplication by -1.
	for (j = 0; j < nR; j++) {
		for (k = 0; k < PARAM_LENGTH; k++) {
			wjacobian[j*PARAM_LENGTH + k] = -jacobian[j*PARAM_LENGTH + k];
		}
	}
	
	// initializations
	for (k = 0; k < nC; ++k) {
		permutation[k] = k;
		norm2 = 0.0f;
		
		for (int i = 0; i < nR; ++i) {
			akk = toData(wjacobian,i,k);
			norm2 += akk * akk;
		}
		jacNorm[k] = sqrtf(norm2);
	}

	// transform the matrix column after column
	for (k = 0; k < nC; ++k) {

		// select the column with the greatest norm on active components
		nextColumn = -1;
		ak2 = -INFINITY;
		for (i = k; i < nC; ++i) {
			norm2 = 0.f;
			for (j = k; j < nR; ++j) {
				aki = toData(wjacobian,j,permutation[i]);
				norm2 += aki * aki;
			}
			if (isinf(norm2) || isnan(norm2)) {
				return wjacobian; //UNABLE_TO_PERFORM_QR_DECOMPOSITION_ON_JACOBIAN;
			}
			if (norm2 > ak2) {
				nextColumn = i;
				ak2 = norm2;
			}
		}
		if (ak2 <= qrRankingThreshold) {
			*rank=k; 
		}
		pk = permutation[nextColumn];
		permutation[nextColumn] = permutation[k];
		permutation[k] = pk;

		// choose alpha such that Hk.u = alpha ek
		akk = toData(wjacobian,k,pk);
		alpha = (akk > 0.f) ? -sqrtf(ak2) : sqrtf(ak2);
		betak = 1.0f / (ak2 - akk * alpha);
		beta[pk] = betak;

		// transform the current column
		diagR[pk] = alpha;
		wjacobian[k*nC+pk] -= alpha;

		// transform the remaining columns
		for (dk = nC - 1 - k; dk > 0; --dk) {
			gamma = 0.0f;
			for (j = k; j < nR; ++j) {
				gamma += toData(wjacobian,j,pk) * toData(wjacobian,j,permutation[k + dk]);
			}
			gamma *= betak;
			for (j = k; j < nR; ++j) {
				wjacobian[j*nC+permutation[k + dk]] -= gamma * toData(wjacobian,j,pk);
			}
		}
	}
	*rank = solvedCols;
	return wjacobian;
}

__device__ void determineLMDirection(const int solvedCols, float* diagR, int* permutation, float* lmDir, float* weightedJacobian, 
	float* qy, float* diag, float* lmDiag, float* work) {

	int i, j, pj, k, pk;
	float dpj, qtbpj, sin, cos, rkk, tan, cotan, temp, rik, temp2;

	// copy R and Qty to preserve input and initialize s
	//  in particular, save the diagonal elements of R in lmDir
	for (j = 0; j < solvedCols; ++j) {
		pj = permutation[j];
		for (i = j + 1; i < solvedCols; ++i) {
			fromData(weightedJacobian,i,pj, toData(weightedJacobian,j,permutation[i]));
		}
		lmDir[j] = diagR[pj];
		work[j] = qy[j];
	}

	// eliminate the diagonal matrix d using a Givens rotation
	for (j = 0; j < solvedCols; ++j) {

		// prepare the row of d to be eliminated, locating the
		// diagonal element using p from the Q.R. factorization
		pj = permutation[j];
		dpj = diag[pj];
		if (dpj != 0.f) {
			for (k = j + 1; k < PARAM_LENGTH; k++)
				lmDiag[k]=0.f;
		}
		lmDiag[j] = dpj;

		//  the transformations to eliminate the row of d
		// modify only a single element of Qty
		// beyond the first n, which is initially zero.
		qtbpj = 0.f;
		for (k = j; k < solvedCols; ++k) {
			pk = permutation[k];

			// determine a Givens rotation which eliminates the
			// appropriate element in the current row of d
			if (lmDiag[k] != 0.f) {

				rkk = toData(weightedJacobian,k,pk);
				if (abs(rkk) < abs(lmDiag[k])) {
					cotan = rkk / lmDiag[k];
					sin = 1.0f / sqrtf(1.0f + cotan * cotan);
					cos = sin * cotan;
				}
				else {
					tan = lmDiag[k] / rkk;
					cos = 1.0f /sqrtf(1.0f + tan * tan);
					sin = cos * tan;
				}

				// compute the modified diagonal element of R and
				// the modified element of (Qty,0)
				fromData(weightedJacobian, k, pk, cos * rkk + sin * lmDiag[k]);
				temp = cos * work[k] + sin * qtbpj;
				qtbpj = -sin * work[k] + cos * qtbpj;
				work[k] = temp;

				// accumulate the tranformation in the row of s
				for (int i = k + 1; i < solvedCols; ++i) {
					rik = toData(weightedJacobian, i, pk);
					temp2 = cos * rik + sin * lmDiag[i];
					lmDiag[i] = -sin * rik + cos * lmDiag[i];
					fromData(weightedJacobian, i, pk, temp2);
				}
			}
		}

		// store the diagonal element of s and restore
		// the corresponding diagonal element of R
		lmDiag[j] = toData(weightedJacobian, j, permutation[j]);
		fromData(weightedJacobian, j, permutation[j], lmDir[j]);
	}

	// solve the triangular system for z, if the system is
	// singular, then obtain a least squares solution
	int nSing = solvedCols;
	for (int j = 0; j < solvedCols; ++j) {
		if ((lmDiag[j] == 0.f) && (nSing == solvedCols)) {
			nSing = j;
		}
		if (nSing < solvedCols) {
			work[j] = 0.f;
		}
	}
	if (nSing > 0) {
		for (int j = nSing - 1; j >= 0; --j) {
			int pj = permutation[j];
			float sum = 0.0f;
			for (int i = j + 1; i < nSing; ++i) {
				sum += toData(weightedJacobian, i, pj) * work[i];
			}
			work[j] = (work[j] - sum) / lmDiag[j];
		}
	}

	// permute the components of z back to components of lmDir
	for (int j = 0; j < PARAM_LENGTH; ++j) {
		lmDir[permutation[j]] = work[j];
	}
}

__device__ void determineLMParameter(const int solvedCols, float* diagR, int* permutation, int rank, float* lmPar, float* lmDir, float* weightedJacobian,
	float* qy, const float delta, float* diag, float* work1, float* work2, float* work3) {
	int nC = PARAM_LENGTH;
	float dxNorm, s, fp, ypk;
	int i, j, k, pj, pk;

	// compute and store in x the gauss-newton direction, if the
	// jacobian is rank-deficient, obtain a least squares solution
	for (j = 0; j < rank; ++j) {
		lmDir[permutation[j]] = qy[j];
	}
	for (j = rank; j < nC; ++j) {
		lmDir[permutation[j]] = 0.f;
	}
	for (k = rank - 1; k >= 0; --k) {
		pk = permutation[k];
		ypk = lmDir[pk] / diagR[pk];
		for (i = 0; i < k; ++i) {
			lmDir[permutation[i]] -= ypk * toData(weightedJacobian,i,pk);
		}
		lmDir[pk] = ypk;
	}

	// evaluate the function at the origin, and test
	// for acceptance of the Gauss-Newton direction
	dxNorm = 0.0f;
	for (j = 0; j < solvedCols; ++j) {
		pj = permutation[j];
		s = diag[pj] * lmDir[pj];
		work1[pj] = s;
		dxNorm += s * s;
	}
	dxNorm = sqrtf(dxNorm);
	fp = dxNorm - delta;
	if (fp <= 0.1f * delta) {
		*lmPar = 0.f;
		return;
	}

	// if the jacobian is not rank deficient, the Newton step provides
	// a lower bound, parl, for the zero of the function,
	// otherwise set this bound to zero
	float sum2;
	float sum;
	float parl = 0.f;
	if (rank == solvedCols) {
		for (j = 0; j < solvedCols; ++j) {
			pj = permutation[j];
			work1[pj] *= diag[pj] / dxNorm;
		}
		sum2 = 0.f;
		for (j = 0; j < solvedCols; ++j) {
			pj = permutation[j];
			sum = 0.f;
			for (i = 0; i < j; ++i) {
				sum += toData(weightedJacobian,i,pj) * work1[permutation[i]];
			}
			s = (work1[pj] - sum) / diagR[pj];
			work1[pj] = s;
			sum2 += s * s;
		}
		parl = fp / (delta * sum2);
	}

	// calculate an upper bound, paru, for the zero of the function
	sum2 = 0.f;
	for (int j = 0; j < solvedCols; ++j) {
		int pj = permutation[j];
		float sum = 0.0f;
		for (int i = 0; i <= j; ++i) {
			sum += toData(weightedJacobian, i , pj) * qy[i];
		}
		sum /= diag[pj];
		sum2 += sum * sum;
	}
	float gNorm = sqrtf(sum2);
	float paru = gNorm / delta;
	if (paru == 0.f) {
		paru = FLT_EPSILON / min(delta, 0.1f);
	}

	// if the input par lies outside of the interval (parl,paru),
	// set par to the closer endpoint
	*lmPar = min(paru, max(*lmPar, parl));
	if (*lmPar == 0.f) {
		*lmPar = gNorm / dxNorm;
	}

	int countdown;
	float sPar, previousFP, tmp;
	for (countdown = 10; countdown >= 0; --countdown) {

		// evaluate the function at the current value of lmPar
		if (*lmPar == 0.f) {
			*lmPar = max(FLT_EPSILON, 0.001f * paru);
		}
		sPar = sqrtf(*lmPar);
		for (j = 0; j < solvedCols; ++j) {
			pj = permutation[j];
			work1[pj] = sPar * diag[pj];
		}
		determineLMDirection(solvedCols,diagR,permutation,lmDir,weightedJacobian,
			qy, work1, work2, work3);

		dxNorm = 0.f;
		for (j = 0; j < solvedCols; ++j) {
			pj = permutation[j];
			s = diag[pj] * lmDir[pj];
			work3[pj] = s;
			dxNorm += s * s;
		}
		dxNorm = sqrtf(dxNorm);
		previousFP = fp;
		fp = dxNorm - delta;

		// if the function is small enough, accept the current value
		// of lmPar, also test for the exceptional cases where parl is zero
		if ((abs(fp) <= 0.1f * delta) ||
			((parl == 0.f) && (fp <= previousFP) && (previousFP < 0.f))) {
			return;
		}

		// compute the Newton correction
		for (j = 0; j < solvedCols; ++j) {
			pj = permutation[j];
			work1[pj] = work3[pj] * diag[pj] / dxNorm;
		}
		for (j = 0; j < solvedCols; ++j) {
			pj = permutation[j];
			work1[pj] /= work2[j];
			tmp = work1[pj];
			for (i = j + 1; i < solvedCols; ++i) {
				work1[permutation[i]] -= toData(weightedJacobian, i, pj) * tmp;
			}
		}
		sum2 = 0.f;
		for (j = 0; j < solvedCols; ++j) {
			s = work1[permutation[j]];
			sum2 += s * s;
		}
		float correction = fp / (delta * sum2);

		// depending on the sign of the function, update parl or paru.
		if (fp > 0.f) {
			parl = max(parl, *lmPar);
		}
		else if (fp < 0.f) {
			paru = min(paru, *lmPar);
		}

		// compute an improved estimate for lmPar
		*lmPar = max(parl, *lmPar + correction);
	}
}

//***************************************************************************************************************************
__device__ void kernel_CentroidFitter(const int sz, const float *data, float *sx, float *sy,
	float *sx_std, float *sy_std){

	float tmpsx = 0.0f; float tmpsx_std = 0.0f;
	float tmpsy = 0.0f; float tmpsy_std = 0.0f;
	float tmpsum = 0.0f;
	int ii, jj;
	int index = 0;
	float s = 0.f;

	for (jj = 0; jj<sz; jj++)
		for (ii = 0; ii<sz; ii++){
			index = sz*jj + ii;
			s = data[index];
			tmpsx += s * ii;
			tmpsy += s * jj;
			tmpsum += s;
		}

	*sx = tmpsx / tmpsum;
	*sy = tmpsy / tmpsum;

	for (ii = 0; ii<sz; ii++)
		for (jj = 0; jj<sz; jj++) {
			index = sz*jj + ii;
			s = data[index];
			tmpsx_std += s*(ii - *sx)*(ii - *sx);
			tmpsy_std += s*(jj - *sy)*(jj - *sy);
		}

	*sx_std = sqrtf(tmpsx_std / tmpsum / sz);
	*sy_std = sqrtf(tmpsy_std / tmpsum / sz);
}

//***************************************************************************************************************************

extern "C"
__global__ void kernel_LM(float *d_Parameters, float* target, const int size, int iterations,const int Nfits){
	
	int tx = threadIdx.x;
	int bx = blockIdx.x;
	int BlockSize = blockDim.x;

	//Prevent read/write past end of array
	if ((bx*BlockSize + tx) >= Nfits) return;
	if (size > IMSZBIG) return;

	//load data
	float *s_data = target + (size*size*bx*BlockSize + size*size*tx);

	//initial values
	const int nR = size*size; // Number of observed data.
	const int nE = IMSZBIG * IMSZBIG; // maximum number of data
	const int nC = PARAM_LENGTH; // Number of parameters.
	float start[nC]; memset(start, 0, nC * sizeof(float));
	kernel_CentroidFitter(size, s_data, &start[X0], &start[Y0], &start[SX], &start[SY]);
	start[I0] = SHRT_MAX - SHRT_MIN;

	int iterationCounter = 0;
	int evaluationCounter = 0;

	// arrays shared with the other private methods
	int solvedCols = min(nR, nC);
	float diagR[nC]; memset(diagR, 0, nC * sizeof(float));
	float jacNorm[nC]; memset(jacNorm, 0, nC * sizeof(float));
	float beta[nC]; memset(beta, 0, nC * sizeof(float));
	int permutation[nC]; memset(permutation, 0, nC * sizeof(int));
	float lmDir[nC]; memset(lmDir, 0, nC * sizeof(float));
	float lmPar = 0.f;
	int rank = 0;

	// local point
	float   delta = 0.f;
	float   xNorm = 0.f;
	float diag[nC]; memset(diag, 0, nC * sizeof(float));
	float oldX[nC]; memset(oldX, 0, nC * sizeof(float));
	float oldRes[nE]; memset(oldRes, 0, nE * sizeof(float));
	float qtf[nE]; memset(qtf, 0, nE * sizeof(float));
	float work1[nC]; memset(work1, 0, nC * sizeof(float));
	float work2[nC]; memset(work2, 0, nC * sizeof(float));
	float work3[nC]; memset(work3, 0, nC * sizeof(float));

	float currentValues[nE]; memset(currentValues, 0, nE * sizeof(float));
	float currentResiduals[nE]; memset(currentResiduals, 0, nE * sizeof(float));
	float jacobian[nE*nC]; memset(jacobian, 0, nE*nC*sizeof(float));
	float currentPoint[nC]; memset(currentPoint, 0, nC * sizeof(float));
	float weightedJacobian[nE*nC];
    float weightedResidual[nE];
	float tmpVec[nE]; memset(tmpVec, 0, nE * sizeof(float));
	float previousPoint[nC]; memset(previousPoint, 0, nC * sizeof(float));

	//temporary variables
	int i, j, k, pk, pj;
	float dk, xk, s, r, sum;
	float maxCosine = 0.f;
	float ratio, lmNorm, previousCost, actRed, dirJ, coeff1, coeff2, pc2, preRed, dirDer, tmp;

	// Evaluate the function at the starting point and calculate its norm.
	//value will be reassigned in the loop
	evaluationCounter++;
	getValues(start, size, currentValues);
	subtract(target, currentValues, nR, currentResiduals);
	getJacobian(start, size, jacobian);
	float currentCost = getCost(currentResiduals, nR);
	memcpy(currentPoint,start,nC*sizeof(float));
	
	// Outer loop.
	bool firstIteration = true;
	while (true) { 
		
		iterationCounter++;
		memcpy(previousPoint, currentPoint, nC * sizeof(float));
		// QR decomposition of the jacobian matrix
		memset(weightedJacobian, 0, nE*nC*sizeof(float));
		qrDecomposition(solvedCols, diagR, jacNorm, beta, permutation, &rank, jacobian, nR, weightedJacobian);

		//residuals already have weights applied
		memset(weightedResidual, 0, nE * sizeof(float));
		memcpy(weightedResidual, currentResiduals, nR*sizeof(float));

		for (i = 0; i < nR; i++) 
			qtf[i] = weightedResidual[i];
		
		// compute Qt.res
		qTy(beta, permutation, weightedJacobian, nR, qtf);

		// now we don't need Q anymore,
		// so let jacobian contain the R matrix with its diagonal elements
		for (k = 0; k < solvedCols; ++k) {
			pk = permutation[k];
			fromData(weightedJacobian,k,pk,diagR[pk]);
		}

		if (firstIteration) {
			// scale the point according to the norms of the columns
			// of the initial jacobian
			xNorm = 0.f;
			for (k = 0; k < nC; ++k) {
				dk = jacNorm[k];
				if (dk == 0) {
					dk = 1.0f;
				}
				xk = dk * currentPoint[k];
				xNorm += xk * xk;
				diag[k] = dk;
			}
			xNorm = sqrtf(xNorm);

			// initialize the step bound delta
			delta = (xNorm == 0) ? initialStepBoundFactor : (initialStepBoundFactor * xNorm);
		}

		// check orthogonality between function vector and jacobian columns
		maxCosine = 0.f;
		if (currentCost != 0.f) {
			for (j = 0; j < solvedCols; ++j) {
				pj = permutation[j];
				s = jacNorm[pj];
				if (s != 0.f) {
					sum = 0.f;
					for (i = 0; i <= j; ++i) {
						sum += toData(weightedJacobian,i,pj) * qtf[i];
					}
					maxCosine = max(maxCosine, abs(sum) / (s * currentCost));
				}
			}
		}
		// Convergence has been reached.
		if (maxCosine <= orthoTolerance) goto end; 

		// rescale if necessary
		for (j = 0; j < nC; ++j) 
			diag[j] = max(diag[j], jacNorm[j]);

		// Inner loop.
		for (ratio = 0.f; ratio < 1.0e-4f;) {

			// save the state
			for (j = 0; j < solvedCols; ++j) {
				pj = permutation[j];
				oldX[pj] = currentPoint[pj];
			}
			previousCost = currentCost;
			
			memcpy(tmpVec, weightedResidual, nR * sizeof(float));
			memcpy(weightedResidual, oldRes, nR * sizeof(float));
			memcpy(oldRes, tmpVec, nR * sizeof(float));

			// determine the Levenberg-Marquardt parameter
			determineLMParameter(solvedCols, diagR, permutation, rank, &lmPar, lmDir, weightedJacobian,
				qtf, delta, diag, work1, work2, work3);

			// compute the new point and the norm of the evolution direction
			lmNorm = 0.f;
			for (j = 0; j < solvedCols; ++j) {
				pj = permutation[j];
				lmDir[pj] = -lmDir[pj];
				currentPoint[pj] = oldX[pj] + lmDir[pj];
				s = diag[pj] * lmDir[pj];
				lmNorm += s * s;
			}
			lmNorm = sqrtf(lmNorm);
			// on the first iteration, adjust the initial step bound.
			if (firstIteration) {
				delta = min(delta, lmNorm);
			}

			// Evaluate the function at x + p and calculate its norm.
			memset(currentValues, 0, nR * sizeof(float));
			memset(currentResiduals, 0, nR * sizeof(float));
			memset(jacobian, 0, nR*nC * sizeof(float));
			evaluationCounter++;
			getValues(currentPoint, size,currentValues);
			subtract(target, currentValues, nR, currentResiduals);
			getJacobian(currentPoint, size, jacobian);
			currentCost = getCost(currentResiduals, nR);

			// compute the scaled actual reduction
			actRed = -1.0f;
			if (0.1f * currentCost < previousCost) {
				if (currentCost == previousCost)
					previousCost += 10.f*FLT_EPSILON;
				r = currentCost / previousCost;
				actRed = 1.0f - r * r;
			}

			// compute the scaled predicted reduction
			// and the scaled directional derivative
			for (j = 0; j < solvedCols; ++j) {
				pj = permutation[j];
				dirJ = lmDir[pj];
				work1[j] = 0.f;
				for (i = 0; i <= j; ++i) {
					work1[i] += toData(weightedJacobian,i,pj) * dirJ;
				}
			}
			coeff1 = 0.f;
			for (j = 0; j < solvedCols; ++j) {
				coeff1 += work1[j] * work1[j];
			}
			pc2 = previousCost * previousCost;
			coeff1 /= pc2;
			coeff2 = lmPar * lmNorm * lmNorm / pc2;
			preRed = coeff1 + 2.f * coeff2;
			dirDer = -(coeff1 + coeff2);

			// ratio of the actual to the predicted reduction
			ratio = (preRed == 0.f) ? 0.f : (actRed / preRed);

			// update the step bound
			if (ratio <= 0.25f) {
				tmp = (actRed < 0.f) ? (0.5f * dirDer / (dirDer + 0.5f * actRed)) : 0.5f;
				if ((0.1f * currentCost >= previousCost) || (tmp < 0.1f)) {
					tmp = 0.1f;
				}
				delta = tmp * min(delta, 10.0f * lmNorm);
				lmPar /= tmp;

			}
			else if ((lmPar == 0.f) || (ratio >= 0.75f)) {
				delta = 2.f * lmNorm;
				lmPar *= 0.5f;
			}

			// test for successful iteration.
 			if (ratio >= 1.0e-4f) {
				// successful iteration, update the norm
				firstIteration = false;
				xNorm = 0.f;
				for (k = 0; k < nC; ++k) {
					xk = diag[k] * currentPoint[k];
					xNorm += xk * xk;
				}
				xNorm = sqrtf(xNorm);

				// tests for convergence.
				if (converged(iterationCounter, previousPoint, currentPoint)==0) 
					goto end;
			}
			else {
				// failed iteration, reset the previous values
				currentCost = previousCost;
				for (i = 0; i < solvedCols; ++i) {
					pj = permutation[i];
					currentPoint[pj] = oldX[pj];
				}
				memcpy(tmpVec,weightedResidual, nR * sizeof(float));
				memcpy(weightedResidual, oldRes, nR * sizeof(float));
				memcpy(oldRes, tmpVec, nR * sizeof(float));
				// Reset "current" to previous values.
				memcpy(currentPoint, previousPoint, nC * sizeof(float));
			}

			// Default convergence criteria.
			if ((abs(actRed) <= costRelativeTolerance &&
				preRed <= costRelativeTolerance &&
				ratio <= 2.0f) ||
				delta <= parRelativeTolerance * xNorm) 
				goto end;

			// tests for termination and stringent tolerances
			if (abs(actRed) <= 2.f*FLT_EPSILON &&
				preRed <= 2.f*FLT_EPSILON &&
				ratio <= 2.0f) 
				return;
				//throw new ConvergenceException(LocalizedFormats.TOO_SMALL_COST_RELATIVE_TOLERANCE, costRelativeTolerance);
			
			else if (delta <= 2.f * FLT_EPSILON * xNorm) 
				return;
				//throw new ConvergenceException(LocalizedFormats.TOO_SMALL_PARAMETERS_RELATIVE_TOLERANCE, parRelativeTolerance);
			
			else if (maxCosine <= 2.f * FLT_EPSILON) 
				return;
				//throw new ConvergenceException(LocalizedFormats.TOO_SMALL_ORTHOGONALITY_TOLERANCE, orthoTolerance);
		}
	}
	end:

	//write to global arrays
	d_Parameters[BlockSize*bx + tx] = currentPoint[X0];
	d_Parameters[Nfits + BlockSize*bx + tx] = currentPoint[Y0];
	d_Parameters[Nfits * 2 + BlockSize*bx + tx] = currentPoint[SX];
	d_Parameters[Nfits * 3 + BlockSize*bx + tx] = currentPoint[SY];
	d_Parameters[Nfits * 4 + BlockSize*bx + tx] = currentPoint[I0];
	d_Parameters[Nfits * 5 + BlockSize*bx + tx] = currentPoint[BG];
	d_Parameters[Nfits * 6 + BlockSize*bx + tx] = (float)iterationCounter;
	d_Parameters[Nfits * 7 + BlockSize*bx + tx] = (float)(bx*BlockSize + tx);
}
