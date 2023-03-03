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
#include "souffle/utility/StringUtil.h"
#include "souffle/utility/StreamUtil.h"
#include "souffle/utility/ContainerUtil.h"
#include <iostream>
#include <string>
#include <vector>
#include <map>
#include <set>
#include <queue>
#include <fstream>

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
        return getRelNamesFromRels(relations);
    }

    std::vector<std::string> getRelSigns() {
        std::vector<souffle::Relation*> relations = program->getAllRelations();
        // TODO: keep only IO rel signs?
        return getRelSignsFromRels(relations);
    }

    std::vector<std::string> getInputRelNames() {
        std::vector<souffle::Relation*> relations = program->getInputRelations();
        return getRelNamesFromRels(relations);
    }

    std::vector<std::string> getOutputRelNames() {
        std::vector<souffle::Relation*> relations = program->getOutputRelations();
        return getRelNamesFromRels(relations);
    }

    std::vector<std::string> getInfoRelNames() {
        std::vector<std::string> infoRelNames;
        for (auto& rel: program->getAllRelations()) {
            std::string name = rel->getName();
            if (name.find("@info") == std::string::npos) {
                continue;
            }
            infoRelNames.push_back(name);
        }
        return infoRelNames;
    }

    void printProvenance(const std::string& provDirectory) {
        // similar to ExplainProvenanceImpl::setup()
#ifndef NDEBUG
    std::string logfilename = provDirectory + "/" + "log.txt";
    std::ofstream logfile(logfilename, std::ios::out | std::ios::binary);
#endif

        std::map<std::pair<std::string, std::size_t>, std::vector<std::string>> infoPredicates;
        std::map<std::pair<std::string, std::size_t>, std::string> infoName;
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

#ifndef NDEBUG
    logfile << "Find rule #" << ruleNum << " of " << name << std::endl;
#endif

                // middle fields are body literals
                for (std::size_t i = 1; i + 1 < rel->getArity(); i++) {
                    std::string bodyLit;
                    tuple >> bodyLit;
                    bodyLiterals.push_back(bodyLit);
                }

                // last field is the rule itself
                std::string rule;
                tuple >> rule;

#ifndef NDEBUG
    logfile << rule << std::endl;
#endif

                std::string relName = name.substr(0, name.find(".@info"));
                infoPredicates.insert({std::make_pair(relName, ruleNum), bodyLiterals});
                infoName.insert({std::make_pair(relName, ruleNum), name});
            }
        }

        // similar to ExplainProvenanceImpl::explain

        // directly dumping constraints to file
        std::string fileName = provDirectory + "/" + "cons_all.txt";
        std::ofstream file(fileName, std::ios::out | std::ios::binary);

        // record proven tuples
        std::map<std::string,std::set<std::vector<souffle::RamDomain>>> provenTuples;

        // put all output tuples into worklist
        std::queue<std::pair<std::string,std::vector<souffle::RamDomain> > > worklist;
        std::string targetName = provDirectory + "/" + "targets.list";
        std::ifstream targetFile(targetName, std::ios::in | std::ios::binary);
        if (targetFile.good()) {
            std::string line;
            while (std::getline(targetFile, line)) {
                std::istringstream liness(line);
                std::string relName;
                if (liness >> relName) {
                    std::vector<souffle::RamDomain> tup;
                    souffle::RamUnsigned a;
                    while (liness >> a)
                        tup.push_back(souffle::ramBitCast<souffle::RamDomain>(a));
                    souffle::Relation* rel = program->getRelation(relName);
                    if (rel == nullptr) {
#ifndef NDEBUG
    logfile << "Skip target tuple of unknown relation" << line << std::endl;
#endif
                        continue;
                    }
                    assert ((rel->getPrimaryArity() == tup.size()) && "target tuple has wrong arity");
                    for (auto& tuple: *rel) {
                        bool match = true;
                        for (int i = 0; match && (i < rel->getPrimaryArity()); i++) {
                            match = (tup[i] == tuple[i]);
                        }
                        if (match) {
                            std::vector<souffle::RamDomain> auxTup;
                            for (int i = 0; i < rel->getArity(); i++)
                                auxTup.push_back(tuple[i]);
                            worklist.push(std::make_pair(relName, auxTup));
                        }
                    }
                }
            }
        } else {
            for (auto& rel: program->getOutputRelations()) {
                std::string relName = rel->getName();
                for (auto& tuple: *rel) {
                    std::vector<souffle::RamDomain> tup;
                    for (int i = 0; i < rel->getArity(); i++)
                        tup.push_back(tuple[i]);
                    worklist.push(std::make_pair(relName, tup));
                }
            }
        }



#ifndef NDEBUG
    logfile << "Proving "<< worklist.size() << " output tuples." << std::endl;
#endif

        // recursively explore tuples from backward
        while (!worklist.empty()) {
            std::string headRelName = worklist.front().first;
            std::vector<souffle::RamDomain> headTup = worklist.front().second;
            worklist.pop();
            //   souffle's provenance-built routine has pruned provenance already, but
            // there will still be some duplicate derived tuples, i.e. late DOB tuples
            // are generated first.
            //   we have two choices: 1. *prune* again to only have one derivation only;
            // 2. *expand* all derivations regardless of DOB marks
            auto headRel = program->getRelation(headRelName);
            assert (headRel->getAuxiliaryArity() == 2 && "relation has wrong auxiliary arity");
            int ruleNum = headTup[headRel->getPrimaryArity()];
            int levelNum = headTup[headRel->getPrimaryArity()+1];
            headTup.erase(headTup.begin() + headRel->getPrimaryArity(), headTup.end());

            // check if head is input
            if (levelNum == 0) {
                continue;
            }

            // check if head has been proven or mark it
            auto head = std::make_pair(headRelName, headTup);
            if (provenTuples[headRelName].find(headTup) != provenTuples[headRelName].end())
                continue;
            provenTuples[headRelName].insert(headTup);

            std::string headAtom = decodeRelTuple(headRelName, headTup);

#ifndef NDEBUG
    logfile << "Exploreing proof of " << headAtom
            << ";" << headTup[headRel->getPrimaryArity()]
            << "," << headTup[headRel->getPrimaryArity()+1]
            << std::endl;
#endif

            // invoke subroutine (<rel>_<ruleNum>_subproof), ret contains N concatenated constraint items
            // Note: <levelNum> restricted possible clauses by date-of-birth;
            //       replacing it with `souffle::MAX_RAM_SIGNED` gets all possible derivations
            headTup.push_back(levelNum);
            std::vector<souffle::RamDomain> ret;
            std::string subroutine = headRelName + "_" + std::to_string(ruleNum) + "_subproof";

#ifndef NDEBUG
    logfile << "> Executing subroutine: " << subroutine << std::endl;
#endif

            program->executeSubroutine(subroutine, headTup, ret);

            auto bodyRelations = infoPredicates.at(std::make_pair(headRelName, ruleNum));
#ifndef NDEBUG
    logfile << "> Fetching proofs, "
            << bodyRelations.size() << " total relations, "
            << ret.size() << " body domains." << std::endl;
#endif
            std::size_t tupleCurInd = 0;
            while (tupleCurInd < ret.size()) {
                // consume a group of body tuples from ret, and generate a proof
                std::vector<std::string> bodyAtoms;
                // Note: The first element of bodyRelations represents the head atom
                for (auto it = bodyRelations.begin() + 1; it < bodyRelations.end(); it++) {
                    std::string bodyLiteral = *it;
                    std::string bodyPredicate = souffle::splitString(bodyLiteral, ',')[0];
                    assert(bodyPredicate.size() > 0 && "body of a relation cannot be empty");
                    std::string bodyRelName = bodyPredicate;
                    bool isNegation = false;
                    if (bodyPredicate[0] == '!' && bodyPredicate != "!=") {
                        isNegation = true;
                        bodyRelName = bodyPredicate.substr(1);
#ifndef NDEBUG
    logfile << ">> Negation: " << bodyPredicate << std::endl;
#endif
                    }
                    bool isConstraint = false;
                    if (souffle::contains(constraintList, bodyPredicate))
                        isConstraint = true;
                    std::size_t arity;
                    std::size_t auxArity;
                    if (isConstraint) {
                        arity = 4;
                        auxArity = 2;
#ifndef NDEBUG
    logfile << ">> Constraint: " << bodyPredicate << std::endl;
#endif
                    } else {
                        arity = program->getRelation(bodyRelName)->getArity();
                        auxArity = program->getRelation(bodyRelName)->getAuxiliaryArity();
                    }
                    std::size_t tupleEnd = tupleCurInd + arity;

                    std::vector<souffle::RamDomain> bodyTup;

                    // first fetch primary domains
                    for (int i = tupleCurInd; i < tupleEnd - auxArity; ++i) {
                        bodyTup.push_back(ret[i]);
                    }

                    std::string bodyAtom = decodeRelTuple(bodyRelName, bodyTup);
                    bodyAtoms.push_back(bodyAtom);

                    // then appending auxiliary domains
                    for (int i = tupleEnd - auxArity; i < tupleEnd; ++i) {
                        bodyTup.push_back(ret[i]);
                    }
#ifndef NDEBUG
    logfile << ">> built body atom: " << bodyAtom
            << ";" << bodyTup[arity-auxArity]
            << "," << bodyTup[arity-auxArity+1]
            << std::endl;
#endif

                    // push normal bodyTuples into worklist
                    if (!isNegation && !isConstraint) {
                        worklist.push(std::make_pair(bodyRelName, bodyTup));
                    }

                    // steo into next bodyAtom of current proof
                    tupleCurInd = tupleEnd;
                }
                // finish one proof, print generated constraints
#ifndef NDEBUG
    logfile << "> Dumping one proof with length " << bodyAtoms.size() << std::endl;
#endif
                file << headAtom;
                for (auto& bodyAtom : bodyAtoms)
                    file << '\t' << bodyAtom;
                file << "\t#" << infoName[std::make_pair(headRelName,ruleNum)];
                file << std::endl;
                file.flush();
                // skip auxiliary domains
                // Note: the return vector has extra info attributes,
                //      including source of head domains and comparison of levelNums
                std::size_t auxRetArity = 2 * headRel->getPrimaryArity() + 2 * (bodyRelations.size() - 1);
#ifndef NDEBUG
    logfile << ">> Extra proof info:";
    for (int i = 0; i < auxRetArity; ++i) {
        logfile << ' ' << ret[tupleCurInd + i];
    }
    logfile << std::endl;
#endif
                tupleCurInd += auxRetArity;
            }
        }
        file.close();
    }

private:
    std::vector<std::string> constraintList = {
        "=", "!=", "<", "<=", ">=", ">", "match", "contains", "not_match", "not_contains"
    };

    inline std::vector<std::string> getRelNamesFromRels(const std::vector<souffle::Relation*>& rels) {
        int relNum = rels.size();
        std::vector<std::string> relNames;
        for (int i = 0; i < relNum; i++) {
            relNames.push_back(rels[i]->getName());
        }
        return relNames;
    }

    inline std::vector<std::string> getRelSignsFromRels(const std::vector<souffle::Relation*>& rels) {
        int relNum = rels.size();
        std::vector<std::string> relSigns;
        for (int i = 0; i < relNum; i++) {
            relSigns.push_back(rels[i]->getName()+rels[i]->getSignature());
        }
        return relSigns;
    }

    inline std::string decodeRelTuple(const std::string& relName, const std::vector<souffle::RamDomain>& nums) const {
        std::stringstream tupleStream;

        auto rel = program->getRelation(relName);

        if (rel == nullptr) {
            // use 'UNK' for unknown relations
            tupleStream << "UNK(";
            if (nums.size() > 0) {
                tupleStream << nums[0];
            }
            for (std::size_t i = 1; i < nums.size(); ++i) {
                tupleStream << ',' << nums[i];
            }
            tupleStream << ")";
        } else {
            std::vector<std::string> decodedArgs;
            for (std::size_t i = 0; i < nums.size(); ++i) {
                decodedArgs.push_back(valueShow(rel->getAttrType(i)[0], nums[i]));
            }
            tupleStream << relName << "(";
            if (decodedArgs.size() > 0)
                tupleStream << decodedArgs[0];
            for (std::size_t i = 1; i < decodedArgs.size(); i++) {
                tupleStream << "," << decodedArgs[i];
            }
            tupleStream << ")";
        }
        return tupleStream.str();
    }

    std::string valueShow(const char type, const souffle::RamDomain value) const {
        switch (type) {
            case 'i': return tfm::format("%d", souffle::ramBitCast<souffle::RamSigned>(value));
            case 'u': return tfm::format("%d", souffle::ramBitCast<souffle::RamUnsigned>(value));
            case 'f': return tfm::format("%f", souffle::ramBitCast<souffle::RamFloat>(value));
            case 's': return tfm::format("\"%s\"", program->getSymbolTable().decode(value));
            case 'r': return tfm::format("record #%d", value);
            default: souffle::fatal("unhandled type attr code");
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
    if (prog == nullptr) {
        return nullptr;
    }
    return new SWIGSouffleProgram(prog);
}
