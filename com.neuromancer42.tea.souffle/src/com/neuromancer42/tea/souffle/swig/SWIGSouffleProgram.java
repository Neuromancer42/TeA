/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package com.neuromancer42.tea.souffle.swig;

public class SWIGSouffleProgram {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected SWIGSouffleProgram(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(SWIGSouffleProgram obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  @SuppressWarnings("deprecation")
  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        SwigInterfaceJNI.delete_SWIGSouffleProgram(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public SWIGSouffleProgram(SWIGTYPE_p_souffle__SouffleProgram program) {
    this(SwigInterfaceJNI.new_SWIGSouffleProgram(SWIGTYPE_p_souffle__SouffleProgram.getCPtr(program)), true);
  }

  public void run() {
    SwigInterfaceJNI.SWIGSouffleProgram_run(swigCPtr, this);
  }

  public void runAll(String inputDirectory, String outputDirectory) {
    SwigInterfaceJNI.SWIGSouffleProgram_runAll(swigCPtr, this, inputDirectory, outputDirectory);
  }

  public void loadAll(String inputDirectory) {
    SwigInterfaceJNI.SWIGSouffleProgram_loadAll(swigCPtr, this, inputDirectory);
  }

  public void printAll(String outputDirectory) {
    SwigInterfaceJNI.SWIGSouffleProgram_printAll(swigCPtr, this, outputDirectory);
  }

  public void dumpInputs() {
    SwigInterfaceJNI.SWIGSouffleProgram_dumpInputs(swigCPtr, this);
  }

  public void dumpOutputs() {
    SwigInterfaceJNI.SWIGSouffleProgram_dumpOutputs(swigCPtr, this);
  }

  public StringVector getRelNames() {
    return new StringVector(SwigInterfaceJNI.SWIGSouffleProgram_getRelNames(swigCPtr, this), true);
  }

  public StringVector getRelSigns() {
    return new StringVector(SwigInterfaceJNI.SWIGSouffleProgram_getRelSigns(swigCPtr, this), true);
  }

  public StringVector getInputRelNames() {
    return new StringVector(SwigInterfaceJNI.SWIGSouffleProgram_getInputRelNames(swigCPtr, this), true);
  }

  public StringVector getOutputRelNames() {
    return new StringVector(SwigInterfaceJNI.SWIGSouffleProgram_getOutputRelNames(swigCPtr, this), true);
  }

  public void printProvenance() {
    SwigInterfaceJNI.SWIGSouffleProgram_printProvenance(swigCPtr, this);
  }

}