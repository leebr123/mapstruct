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
package org.mapstruct.ap.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Generated;

import org.mapstruct.ap.model.common.Accessibility;
import org.mapstruct.ap.model.common.ModelElement;
import org.mapstruct.ap.model.common.Type;
import org.mapstruct.ap.model.common.TypeFactory;
import org.mapstruct.ap.option.Options;
import org.mapstruct.ap.version.VersionInformation;

/**
 * A type generated by MapStruct, e.g. representing a mapper type.
 *
 * @author Gunnar Morling
 */
public abstract class GeneratedType extends ModelElement {

    private static final String JAVA_LANG_PACKAGE = "java.lang";

    private final String packageName;
    private final String name;
    private final String superClassName;
    private final String interfaceName;

    private final List<Annotation> annotations;
    private final List<MappingMethod> methods;
    private final List<? extends Field> fields;
    private final SortedSet<Type> extraImportedTypes;

    private final boolean suppressGeneratorTimestamp;
    private final boolean suppressGeneratorVersionComment;
    private final VersionInformation versionInformation;
    private final Accessibility accessibility;
    private final Constructor constructor;

    /**
     * Type representing the {@code @Generated} annotation
     */
    private final Type generatedType;

    // CHECKSTYLE:OFF
    protected GeneratedType(TypeFactory typeFactory, String packageName, String name, String superClassName,
                            String interfaceName,
                            List<MappingMethod> methods,
                            List<? extends Field> fields,
                            Options options,
                            VersionInformation versionInformation,
                            Accessibility accessibility,
                            SortedSet<Type> extraImportedTypes,
                            Constructor constructor ) {
        this.packageName = packageName;
        this.name = name;
        this.superClassName = superClassName;
        this.interfaceName = interfaceName;
        this.extraImportedTypes = extraImportedTypes;

        this.annotations = new ArrayList<Annotation>();
        this.methods = methods;
        this.fields = fields;

        this.suppressGeneratorTimestamp = options.isSuppressGeneratorTimestamp();
        this.suppressGeneratorVersionComment = options.isSuppressGeneratorVersionComment();
        this.versionInformation = versionInformation;
        this.accessibility = accessibility;

        this.generatedType = typeFactory.getType( Generated.class );
        this.constructor = constructor;
    }

    // CHECKSTYLE:ON

    public String getPackageName() {
        return packageName;
    }

    public String getName() {
        return name;
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    public void addAnnotation(Annotation annotation) {
        annotations.add( annotation );
    }

    public List<MappingMethod> getMethods() {
        return methods;
    }

    public List<? extends ModelElement> getFields() {
        return fields;
    }

    public boolean isSuppressGeneratorTimestamp() {
        return suppressGeneratorTimestamp;
    }

    public boolean isSuppressGeneratorVersionComment() {
        return suppressGeneratorVersionComment;
    }

    public VersionInformation getVersionInformation() {
        return versionInformation;
    }

    public Accessibility getAccessibility() {
        return accessibility;
    }

    @Override
    public SortedSet<Type> getImportTypes() {
        SortedSet<Type> importedTypes = new TreeSet<Type>();
        importedTypes.add( generatedType );

        for ( MappingMethod mappingMethod : methods ) {
            for ( Type type : mappingMethod.getImportTypes() ) {
                addWithDependents( importedTypes, type );
            }
        }

        for ( Field field : fields ) {
            if ( field.isTypeRequiresImport() ) {
                for ( Type type : field.getImportTypes() ) {
                    addWithDependents( importedTypes, type );
                }
            }
        }

        for ( Annotation annotation : annotations ) {
            addWithDependents( importedTypes, annotation.getType() );
        }

        for ( Type extraImport : extraImportedTypes ) {
            addWithDependents( importedTypes, extraImport );
        }

        return importedTypes;
    }

    public Constructor getConstructor() {
        return constructor;
    }

    protected void addWithDependents(Collection<Type> collection, Type typeToAdd) {
        if ( typeToAdd == null ) {
            return;
        }

        if ( needsImportDeclaration( typeToAdd ) ) {
            if ( typeToAdd.isArrayType() ) {
                collection.add( typeToAdd.getComponentType() );
            }
            else {
                collection.add( typeToAdd );
            }
        }

        for ( Type type : typeToAdd.getTypeParameters() ) {
            addWithDependents( collection, type );
        }
    }

    private boolean needsImportDeclaration(Type typeToAdd) {
        if ( !typeToAdd.isImported() ) {
            return false;
        }

        if ( typeToAdd.getPackageName() == null ) {
            return false;
        }

        if ( typeToAdd.getPackageName().startsWith( JAVA_LANG_PACKAGE ) ) {
            return false;
        }

        if ( typeToAdd.getPackageName().equals( packageName ) ) {
            if ( !typeToAdd.getTypeElement().getNestingKind().isNested() ) {
                return false;
            }
        }

        return true;
    }
}
