/*
 * Souffle - A Datalog Compiler
 * Copyright (c) 2019, The Souffle Developers. All rights reserved
 * Licensed under the Universal Permissive License v 1.0 as shown at:
 * - https://opensource.org/licenses/UPL
 * - <souffle root>/licenses/SOUFFLE-UPL.txt
 */

/************************************************************************
 *
 * @file SwigInterface.h
 *
 * Header file for SWIG to invoke functions in souffle::SouffleProgram
 *
 ***********************************************************************/

#pragma once

#include "souffle/SouffleInterface.h"
#include "souffle/provenance/Explain.h"
#include <iostream>
#include <string>
#include <vector>

/**
 * Abstract base class for generated Datalog programs
 */
class SWIGSouffleProgram {
    /**
     * pointer to SouffleProgram to invoke functions from SouffleInterface.h
     */
    souffle::SouffleProgram* program;

public:
    SWIGSouffleProgram(souffle::SouffleProgram* program) : program(program) {}

    virtual ~SWIGSouffleProgram() {
        delete program;
    }

    /**
     * Calls the corresponding method souffle::SouffleProgram::run in SouffleInterface.h
     */
    void run() {
        program->run();
    }

    /**
     * Calls the corresponding method souffle::SouffleProgram::runAll in SouffleInterface.h
     */
    void runAll(const std::string& inputDirectory, const std::string& outputDirectory) {
        program->runAll(inputDirectory, outputDirectory);
    }

    /**
     * Calls the corresponding method souffle::SouffleProgram::loadAll in SouffleInterface.h
     */
    void loadAll(const std::string& inputDirectory) {
        program->loadAll(inputDirectory);
    }

    /**
     * Calls the corresponding method souffle::SouffleProgram::printAll in SouffleInterface.h
     */
    void printAll(const std::string& outputDirectory) {
        program->printAll(outputDirectory);
    }

    /**
     * Calls the corresponding method souffle::SouffleProgram::dumpInputs in SouffleInterface.h
     */
    void dumpInputs() {
        program->dumpInputs();
    }

    /**
     * Calls the corresponding method souffle::SouffleProgram::dumpOutputs in SouffleInterface.h
     */
    void dumpOutputs() {
        program->dumpOutputs();
    }

    std::vector<std::string> getRelNames() {
        std::vector<souffle::Relation*> relations = program->getAllRelations();
        int relNums = relations.size();
        std::vector<const std::string&> relNames = new std::vector<const std::string&>();
        for (int i = 0; i < relNums; i++) {
            relNames.append(relations[i]->getName());
        }
        return relNames;
    }

    void printProvenance() {
        //TODO: See include/souffle/provenance/ExplainProvenanceImpl.h: #56 void setup()
        for (auto& rel: program->getAllRelations()) {
            std::string name = rel->getName();
            if (name.find("@info") == std::string::npos) {
                continue;
            }

            // find all the info tuples
            for (auto& tuple : *rel) {
                std::vector<std::string> bodyLiterals;

                // first field is rule number
                souffle::RamDomain ruleNum;
                tuple >> ruleNum;

                // middle fields are body literals
                for (std::size_t i = 1; i + 1 < rel->getArity(); i++) {
                    std::string bodyLit;
                    tuple >> bodyLit;
                    bodyLiterals.push_back(bodyLit);
                }

                // last field is the rule itself
                std::string rule;
                tuple >> rule;

                std::string relName = name.substr(0, name.find(".@info"));

                // TODO: dump this provenance to files
//                info.insert({std::make_pair(relName, ruleNum), bodyLiterals});
//                rules.insert({std::make_pair(relName, ruleNum), rule});
            }
        }
    }
};

/**
 * Creates an instance of a SWIG souffle::SouffleProgram that can be called within a program of a supported
 * language for the SWIG option specified in main.cpp. This enables the program to use this instance and call
 * the supported souffle::SouffleProgram methods.
 * @param name Name of the datalog file/ instance to be created
 */
SWIGSouffleProgram* newInstance(const std::string& name) {
    auto* prog = souffle::ProgramFactory::newInstance(name);
    return new SWIGSouffleProgram(prog);
}
