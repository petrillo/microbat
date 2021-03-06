/*
 * Copyright (C) 2013 by SUTD (Singapore)
 * All rights reserved.
 *
 * 	Author: SUTD
 *  Version:  $Revision: 1 $
 */

package microbat.model;

import microbat.util.BreakpointUtils;
import sav.common.core.utils.SignatureUtils;

/**
 * @author Yun Lin, modified from LLT
 * 
 */
public class ClassLocation {
	protected String id;
	protected String classCanonicalName;
	protected String methodSign;
	protected int lineNo = -1; // started with 1?

	public ClassLocation(String className, String methodName, int lineNumber) {
		this.classCanonicalName = className;
		this.methodSign = methodName;
		this.lineNo = lineNumber;
	}

	public String getClassCanonicalName() {
		return classCanonicalName;
	}

	public void setClassCanonicalName(String classCanonicalName) {
		this.classCanonicalName = classCanonicalName;
	}

	public String getMethodSign() {
		return methodSign;
	}

	public void setMethodSign(String methodSign) {
		this.methodSign = methodSign;
	}

	public int getLineNumber() {
		return lineNo;
	}

	public void setLineNo(int lineNo) {
		this.lineNo = lineNo;
	}
	
	public String getId() {
		if (id == null) {
			id = BreakpointUtils.getLocationId(this);
		}
		return id;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		return classCanonicalName.hashCode() * prime + lineNo;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		
		if(obj instanceof ClassLocation){
			ClassLocation other = (ClassLocation) obj;
			
			return classCanonicalName.equals(other.getClassCanonicalName())
					&& lineNo == other.getLineNumber();			
		}
		
		return false;
	}

	@Override
	public String toString() {
		return this.classCanonicalName + ", line=" + this.lineNo;
	}
	
	public String getMethodName() {
		return SignatureUtils.extractMethodName(methodSign);
	}
}
