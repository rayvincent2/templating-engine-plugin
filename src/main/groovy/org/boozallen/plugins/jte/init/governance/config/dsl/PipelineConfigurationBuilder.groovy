/*
    Copyright 2018 Booz Allen Hamilton

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package org.boozallen.plugins.jte.init.governance.config.dsl

import static PipelineConfigurationDsl.ConfigBlockMap
import org.codehaus.groovy.runtime.GStringImpl

/**
 * Script base class used during execution of the pipeline configuration DSL.
 * <p>
 * Uses groovy runtime-metaprogramming to translate the nested structure of the
 * configuration into a LinkedHashMap and populate a {@link PipelineConfigurationObject}
 * that has been injected into the binding by {@link PipelineConfigurationDsl}.
 */
abstract class PipelineConfigurationBuilder extends Script{

    List objectStack = []
    List nodeStack = []
    Boolean recordMergeKey = false
    Boolean recordOverrideKey = false

    /*
        used purely to catch syntax errors such as:

        1. someone trying to set a configuraiton key to an unquoted string

            a = b
            vs
            a = "b"

        2. to a block

            a = b{
                c = 3
            }
    */
    static enum BuilderMethod{

        METHOD_MISSING, PROPERTY_MISSING

        String name
        BuilderMethod call(String name){
            this.name = name
            return this
        }

        String getName(){ return name }

    }

    void setMergeToTrue(){
        recordMergeKey = true
    }

    void setOverrideToTrue(){
        recordOverrideKey = true
    }

    @SuppressWarnings(['MethodParameterTypeRequired', 'NoDef'])
    BuilderMethod methodMissing(String name, args){
        objectStack.push([:])
        nodeStack.push(name)

        recordMergeOrOverride()
        args[0]()

        ConfigBlockMap nodeConfig = new ConfigBlockMap(objectStack.pop())
        String nodeName = nodeStack.pop()

        if (objectStack.size()){
            objectStack.last() << [ (nodeName): nodeConfig ]
        } else {
            pipelineConfig.config << [ (name): nodeConfig]
        }
        return BuilderMethod.METHOD_MISSING(name)
    }

    void setProperty(String name, Object value){
        // validate syntax errors
        if (value instanceof BuilderMethod){
            ArrayList ex = [ "Template Configuration File Syntax Error: " ]
            switch(value){
                case BuilderMethod.METHOD_MISSING:
                    ex += "line containing: ${name} = ${value.getName()} { "
                    ex += "cannot set property equal to configuration block"
                    break
                case BuilderMethod.PROPERTY_MISSING:
                    ex += "line containing: ${name} = ${value.getName()} "
                    ex += "Referencing other configs is not permitted, or you forgot to quote the value."
                    ex += "did you mean: ${name} = \"${value.getName()}\""
                    break
            }
            throw new TemplateConfigException(ex.join("\n"))
        }

        Object v = (value instanceof GStringImpl) ? value.toString() : value

        recordMergeOrOverride(name)
        if (objectStack.size()){
            objectStack.last()[name] = v
        } else {
            pipelineConfig.config[name] = v
        }
    }

    BuilderMethod propertyMissing(String name){
        recordMergeOrOverride(name)
        if (objectStack.size()){
            objectStack.last()[name] = [:]
        } else {
            pipelineConfig.config[name] = [:]
        }
        return BuilderMethod.PROPERTY_MISSING(name)
    }

    void recordMergeOrOverride(String name = null){
        if(!recordMergeKey && !recordOverrideKey){
            return
        }

        String key = nodeStack.join(".")
        if(name){
            key += (key.length() ? ".${name}" : name)
        }
        if(recordMergeKey){
            pipelineConfig.merge << key
            recordMergeKey = false
        }
        if(recordOverrideKey){
            pipelineConfig.override << key
            recordOverrideKey = false
        }
    }

}
