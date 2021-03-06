/**
 *  Copyright 2012-2015 Gunnar Morling (http://www.gunnarmorling.de/)
 *  and/or other contributors as indicated by the @authors tag. See the
 *  copyright.txt file in the distribution for a full listing of all
 *  contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.mapstruct.ap.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.mapstruct.ap.model.common.Parameter;
import org.mapstruct.ap.model.common.Type;
import org.mapstruct.ap.model.common.TypeFactory;

import org.mapstruct.ap.model.source.BeanMapping;
import org.mapstruct.ap.model.source.IterableMapping;
import org.mapstruct.ap.model.source.MapMapping;
import org.mapstruct.ap.model.source.Mapping;
import org.mapstruct.ap.model.source.SourceMethod;

import org.mapstruct.ap.prism.BeanMappingPrism;
import org.mapstruct.ap.prism.IterableMappingPrism;
import org.mapstruct.ap.prism.MapMappingPrism;
import org.mapstruct.ap.prism.MappingPrism;
import org.mapstruct.ap.prism.MappingsPrism;

import org.mapstruct.ap.util.AnnotationProcessingException;
import org.mapstruct.ap.util.FormattingMessager;
import org.mapstruct.ap.util.MapperConfiguration;
import org.mapstruct.ap.util.Message;

import static org.mapstruct.ap.util.Executables.getAllEnclosedExecutableElements;

/**
 * A {@link ModelElementProcessor} which retrieves a list of {@link SourceMethod}s
 * representing all the mapping methods of the given bean mapper type as well as
 * all referenced mapper methods declared by other mappers referenced by the
 * current mapper.
 *
 * @author Gunnar Morling
 */
public class MethodRetrievalProcessor implements ModelElementProcessor<Void, List<SourceMethod>> {

    private FormattingMessager messager;
    private TypeFactory typeFactory;
    private Types typeUtils;
    private Elements elementUtils;

    @Override
    public List<SourceMethod> process(ProcessorContext context, TypeElement mapperTypeElement, Void sourceModel) {
        this.messager = context.getMessager();
        this.typeFactory = context.getTypeFactory();
        this.typeUtils = context.getTypeUtils();
        this.elementUtils = context.getElementUtils();

        MapperConfiguration mapperConfig = MapperConfiguration.getInstanceOn( mapperTypeElement );

        if ( !mapperConfig.isValid() ) {
            throw new AnnotationProcessingException(
                "Couldn't retrieve @Mapper annotation",
                mapperTypeElement,
                mapperConfig.getAnnotationMirror() );
        }

        List<SourceMethod> prototypeMethods =
            retrievePrototypeMethods( mapperConfig.getMapperConfigMirror(), mapperConfig );
        return retrieveMethods( mapperTypeElement, mapperTypeElement, mapperConfig, prototypeMethods );
    }

    @Override
    public int getPriority() {
        return 1;
    }

    private List<SourceMethod> retrievePrototypeMethods(TypeMirror typeMirror, MapperConfiguration mapperConfig) {
        if ( typeMirror == null || typeMirror.getKind() == TypeKind.VOID ) {
            return Collections.emptyList();
        }

        TypeElement typeElement = asTypeElement( typeMirror );
        List<SourceMethod> methods = new ArrayList<SourceMethod>();
        for ( ExecutableElement executable : getAllEnclosedExecutableElements( elementUtils, typeElement ) ) {

            ExecutableType methodType = typeFactory.getMethodType( typeElement, executable );
            List<Parameter> parameters = typeFactory.getParameters( methodType, executable );
            boolean containsTargetTypeParameter = SourceMethod.containsTargetTypeParameter( parameters );

            // prototype methods don't have prototypes themselves
            List<SourceMethod> prototypeMethods = Collections.emptyList();

            SourceMethod method =
                getMethodRequiringImplementation(
                    methodType,
                    executable,
                    parameters,
                    containsTargetTypeParameter,
                    mapperConfig,
                    prototypeMethods );

            if ( method != null ) {
                methods.add( method );
            }
        }

        return methods;
    }

    /**
     * Retrieves the mapping methods declared by the given mapper type.
     *
     * @param usedMapper The type of interest (either the mapper to implement or a used mapper via @uses annotation)
     * @param mapperToImplement the top level type (mapper) that requires implementation
     * @param mapperConfig the mapper config
     * @param prototypeMethods prototype methods defined in mapper config type
     * @return All mapping methods declared by the given type
     */
    private List<SourceMethod> retrieveMethods(TypeElement usedMapper, TypeElement mapperToImplement,
                                               MapperConfiguration mapperConfig, List<SourceMethod> prototypeMethods) {
        List<SourceMethod> methods = new ArrayList<SourceMethod>();

        for ( ExecutableElement executable : getAllEnclosedExecutableElements( elementUtils, usedMapper ) ) {
            SourceMethod method = getMethod(
                usedMapper,
                executable,
                mapperToImplement,
                mapperConfig,
                prototypeMethods );

            if ( method != null ) {
                methods.add( method );
            }
        }

        //Add all methods of used mappers in order to reference them in the aggregated model
        if ( usedMapper.equals( mapperToImplement ) ) {
            for ( TypeMirror mapper : mapperConfig.uses() ) {
                methods.addAll( retrieveMethods(
                    asTypeElement( mapper ),
                    mapperToImplement,
                    mapperConfig,
                    prototypeMethods ) );
            }
        }

        return methods;
    }

    private TypeElement asTypeElement(TypeMirror usedMapper) {
        return (TypeElement) ( (DeclaredType) usedMapper ).asElement();
    }

    private SourceMethod getMethod(TypeElement usedMapper,
                                   ExecutableElement method,
                                   TypeElement mapperToImplement,
                                   MapperConfiguration mapperConfig,
                                   List<SourceMethod> prototypeMethods) {

        ExecutableType methodType = typeFactory.getMethodType( usedMapper, method );
        List<Parameter> parameters = typeFactory.getParameters( methodType, method );
        boolean methodRequiresImplementation = method.getModifiers().contains( Modifier.ABSTRACT );
        boolean containsTargetTypeParameter = SourceMethod.containsTargetTypeParameter( parameters );

        //add method with property mappings if an implementation needs to be generated
        if ( ( usedMapper.equals( mapperToImplement ) ) && methodRequiresImplementation ) {
            return getMethodRequiringImplementation( methodType,
                method,
                parameters,
                containsTargetTypeParameter,
                mapperConfig,
                prototypeMethods );
        }
        //otherwise add reference to existing mapper method
        else if ( isValidReferencedMethod( parameters ) || isValidFactoryMethod( parameters ) ) {
            return getReferencedMethod( usedMapper, methodType, method, mapperToImplement, parameters );
        }
        else {
            return null;
        }
    }

    private SourceMethod getMethodRequiringImplementation(ExecutableType methodType, ExecutableElement method,
                                                          List<Parameter> parameters,
                                                          boolean containsTargetTypeParameter,
                                                          MapperConfiguration mapperConfig,
                                                          List<SourceMethod> prototypeMethods) {
        Type returnType = typeFactory.getReturnType( methodType );
        List<Type> exceptionTypes = typeFactory.getThrownTypes( methodType );
        List<Parameter> sourceParameters = extractSourceParameters( parameters );
        Parameter targetParameter = extractTargetParameter( parameters );
        Type resultType = selectResultType( returnType, targetParameter );

        boolean isValid = checkParameterAndReturnType(
            method,
            sourceParameters,
            targetParameter,
            resultType,
            returnType,
            containsTargetTypeParameter
        );

        if ( !isValid ) {
            return null;
        }

        return new SourceMethod.Builder()
                .setExecutable( method )
                .setParameters( parameters )
                .setReturnType( returnType )
                .setExceptionTypes( exceptionTypes )
                .setMappings( getMappings( method) )
                .setIterableMapping( IterableMapping.fromPrism(
                    IterableMappingPrism.getInstanceOn( method ), method, messager ) )
                .setMapMapping(
                    MapMapping.fromPrism( MapMappingPrism.getInstanceOn( method ), method, messager ) )
                .setBeanMapping(
                    BeanMapping.fromPrism( BeanMappingPrism.getInstanceOn( method ), method, messager ) )
                .setTypeUtils( typeUtils )
                .setMessager( messager )
                .setTypeFactory( typeFactory )
                .setMapperConfiguration( mapperConfig )
                .setPrototypeMethods( prototypeMethods )
                .build();
    }

    private SourceMethod getReferencedMethod(TypeElement usedMapper, ExecutableType methodType,
                                             ExecutableElement method, TypeElement mapperToImplement,
                                             List<Parameter> parameters) {
        Type returnType = typeFactory.getReturnType( methodType );
        List<Type> exceptionTypes = typeFactory.getThrownTypes( methodType );
        Type usedMapperAsType = typeFactory.getType( usedMapper );
        Type mapperToImplementAsType = typeFactory.getType( mapperToImplement );

        if ( !mapperToImplementAsType.canAccess( usedMapperAsType, method ) ) {
            return null;
        }

        return new SourceMethod.Builder()
                .setDeclaringMapper( usedMapper.equals( mapperToImplement ) ? null : usedMapperAsType )
                .setExecutable( method )
                .setParameters( parameters )
                .setReturnType( returnType )
                .setExceptionTypes( exceptionTypes )
                .setTypeUtils( typeUtils )
                .setTypeFactory( typeFactory )
                .build();
    }

    private boolean isValidReferencedMethod(List<Parameter> parameters) {
     int validSourceParameters = 0;
        int targetParameters = 0;
        int targetTypeParameters = 0;

        for ( Parameter param : parameters ) {
            if ( param.isMappingTarget() ) {
                targetParameters++;
            }

            if ( param.isTargetType() ) {
                targetTypeParameters++;
            }

            if ( !param.isMappingTarget() && !param.isTargetType() ) {
                validSourceParameters++;
            }
        }
        return validSourceParameters == 1
            && targetParameters <= 1
            && targetTypeParameters <= 1
            && parameters.size() == validSourceParameters + targetParameters + targetTypeParameters;
    }

    private boolean isValidFactoryMethod(List<Parameter> parameters) {
        int validSourceParameters = 0;
        int targetParameters = 0;
        int targetTypeParameters = 0;

        for ( Parameter param : parameters ) {
            if ( param.isMappingTarget() ) {
                targetParameters++;
            }

            if ( param.isTargetType() ) {
                targetTypeParameters++;
            }

            if ( !param.isMappingTarget() && !param.isTargetType() ) {
                validSourceParameters++;
            }
        }
        return validSourceParameters == 0
            && targetParameters == 0
            && targetTypeParameters <= 1
            && parameters.size() == validSourceParameters + targetParameters + targetTypeParameters;
    }

    private Parameter extractTargetParameter(List<Parameter> parameters) {
        for ( Parameter param : parameters ) {
            if ( param.isMappingTarget() ) {
                return param;
            }
        }

        return null;
    }

    private List<Parameter> extractSourceParameters(List<Parameter> parameters) {
        List<Parameter> sourceParameters = new ArrayList<Parameter>( parameters.size() );
        for ( Parameter param : parameters ) {
            if ( !param.isMappingTarget() ) {
                sourceParameters.add( param );
            }
        }

        return sourceParameters;
    }

    private Type selectResultType(Type returnType, Parameter targetParameter) {
        if ( null != targetParameter ) {
            return targetParameter.getType();
        }
        else {
            return returnType;
        }
    }

    private boolean checkParameterAndReturnType(ExecutableElement method, List<Parameter> sourceParameters,
                                                Parameter targetParameter, Type resultType, Type returnType,
                                                boolean containsTargetTypeParameter) {
        if ( sourceParameters.isEmpty() ) {
            messager.printMessage( method, Message.RETRIEVAL_NO_INPUT_ARGS );
            return false;
        }

        if ( targetParameter != null && ( sourceParameters.size() + 1 != method.getParameters().size() ) ) {
            messager.printMessage( method, Message.RETRIEVAL_DUPLICATE_MAPPING_TARGETS );
            return false;
        }

        if ( resultType.getTypeMirror().getKind() == TypeKind.VOID ) {
            messager.printMessage( method, Message.RETRIEVAL_VOID_MAPPING_METHOD );
            return false;
        }

        if ( returnType.getTypeMirror().getKind() != TypeKind.VOID &&
            !resultType.isAssignableTo( returnType ) ) {
            messager.printMessage( method, Message.RETRIEVAL_NON_ASSIGNABLE_RESULTTYPE );
            return false;
        }

        Type parameterType = sourceParameters.get( 0 ).getType();

        if ( parameterType.isIterableType() && !resultType.isIterableType() ) {
            messager.printMessage( method, Message.RETRIEVAL_ITERABLE_TO_NON_ITERABLE );
            return false;
        }

        if ( containsTargetTypeParameter ) {
            messager.printMessage( method, Message.RETRIEVAL_MAPPING_HAS_TARGET_TYPE_PARAMETER );
            return false;
        }

        if ( !parameterType.isIterableType() && resultType.isIterableType() ) {
            messager.printMessage( method, Message.RETRIEVAL_NON_ITERABLE_TO_ITERABLE );
            return false;
        }

        if ( parameterType.isPrimitive() ) {
            messager.printMessage( method, Message.RETRIEVAL_PRIMITIVE_PARAMETER );
            return false;
        }

        if ( resultType.isPrimitive() ) {
            messager.printMessage( method, Message.RETRIEVAL_PRIMITIVE_RETURN );
            return false;
        }

        if ( parameterType.isEnumType() && !resultType.isEnumType() ) {
            messager.printMessage( method, Message.RETRIEVAL_ENUM_TO_NON_ENUM );
            return false;
        }

        if ( !parameterType.isEnumType() && resultType.isEnumType() ) {
            messager.printMessage( method, Message.RETRIEVAL_NON_ENUM_TO_ENUM );
            return false;
        }

        return true;
    }

    /**
     * Retrieves the mappings configured via {@code @Mapping} from the given
     * method.
     *
     * @param method The method of interest
     *
     * @return The mappings for the given method, keyed by target property name
     */
    private Map<String, List<Mapping>> getMappings(ExecutableElement method) {
        Map<String, List<Mapping>> mappings = new HashMap<String, List<Mapping>>();

        MappingPrism mappingAnnotation = MappingPrism.getInstanceOn( method );
        MappingsPrism mappingsAnnotation = MappingsPrism.getInstanceOn( method );

        if ( mappingAnnotation != null ) {
            if ( !mappings.containsKey( mappingAnnotation.target() ) ) {
                mappings.put( mappingAnnotation.target(), new ArrayList<Mapping>() );
            }
            Mapping mapping = Mapping.fromMappingPrism( mappingAnnotation, method, messager );
            if ( mapping != null ) {
                mappings.get( mappingAnnotation.target() ).add( mapping );
            }
        }

        if ( mappingsAnnotation != null ) {
            mappings.putAll( Mapping.fromMappingsPrism( mappingsAnnotation, method, messager ) );
        }

        return mappings;
    }
}
