/*
 * Souffle - A Datalog Compiler
 * Copyright (c) 2019, The Souffle Developers. All rights reserved
 * Licensed under the Universal Permissive License v 1.0 as shown at:
 * - https://opensource.org/licenses/UPL
 * - <souffle root>/licenses/SOUFFLE-UPL.txt
 */

/************************************************************************
 *
 * @file SwigInterface.i
 *
 * SWIG interface file that transforms SwigInterface.h to the necessary language and creates a wrapper file
 * for it
 *
 ***********************************************************************/

%module SwigInterface
%include "std_string.i"
%include "std_vector.i"
namespace std {
    %template(StringVector) vector<string>;
}

%{
#include "souffle-swig-interface.h"
#include <iostream>
#include <map>
#include <string>
#include <vector>
%}

%include "souffle-swig-interface.h"

%newobject newInstance;
SWIGSouffleProgram* newInstance(const std::string& name);
