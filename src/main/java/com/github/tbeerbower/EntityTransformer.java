package com.github.tbeerbower;

import com.github.tbeerbower.controllers.BaseController;
import com.github.tbeerbower.entities.Department;
import de.icongmbh.oss.maven.plugin.javassist.ClassTransformer;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.build.JavassistBuildException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.ClassMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Entity transformer
 */
public class EntityTransformer extends ClassTransformer {
    private static final String TARGET_DIR_PROPERTY_KEY = "target.directory";

    private String targetDir;


    /**
     * We'll only transform entities.
     */
    @Override
    public boolean shouldTransform(final CtClass candidateClass)
            throws JavassistBuildException {
        return candidateClass.hasAnnotation(javax.persistence.Entity.class);
    }

    /**
     * Hack the toString() method.
     */
    @Override
    public void applyTransformations(CtClass classToTransform)
            throws JavassistBuildException {
        // TODO clean up this mess...
        // classToTransform should be an @Entity
        try {
            if (classToTransform.hasAnnotation(javax.persistence.Entity.class)) {
                ClassPool pool = ClassPool.getDefault();
                String classesDir = targetDir + File.separatorChar + "classes" /*+ File.separatorChar + repository.getPackageName().replace('.', File.separatorChar)*/;
                String entityName = classToTransform.getSimpleName();

                String idClassName = "java.lang.Long";
                CtField[] fields = classToTransform.getDeclaredFields();
                for (CtField field : fields) {
                    FieldInfo fieldInfo = field.getFieldInfo();

                    if (field.hasAnnotation(Id.class)) {

                        // TODO : if id class is a primitive then use the wrapper type.
                        idClassName = field.getType().getName();

                        ClassFile classFile = classToTransform.getClassFile();
                        ConstPool constpool = classFile.getConstPool();

                        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) fieldInfo.getAttribute(AnnotationsAttribute.visibleTag);

                        Annotation columnAnnotation = new Annotation(Column.class.getName(), constpool);
                        columnAnnotation.addMemberValue("updatable", new BooleanMemberValue(false, constpool));
                        annotationsAttribute.addAnnotation(columnAnnotation);

                        // SEQUENCE ANNOTATIONS
                        String sequenceName = entityName.toLowerCase() + "_" + field.getName().toLowerCase() + "_seq";
                        Annotation sequenceGeneratorAnnotation = new Annotation(SequenceGenerator.class.getName(), constpool);
                        sequenceGeneratorAnnotation.addMemberValue("name", new StringMemberValue(sequenceName, constpool));
                        sequenceGeneratorAnnotation.addMemberValue("sequenceName", new StringMemberValue(sequenceName, constpool));
                        sequenceGeneratorAnnotation.addMemberValue("allocationSize", new IntegerMemberValue(constpool, 1));
                        annotationsAttribute.addAnnotation(sequenceGeneratorAnnotation);

                        Annotation generatedValueAnnotation = new Annotation(GeneratedValue.class.getName(), constpool);
                        EnumMemberValue enumMemberValue = new EnumMemberValue(constpool);
                        enumMemberValue.setType(GenerationType.class.getName());
                        enumMemberValue.setValue(GenerationType.SEQUENCE.name());
                        generatedValueAnnotation.addMemberValue("strategy", enumMemberValue);
                        generatedValueAnnotation.addMemberValue("generator", new StringMemberValue(sequenceName, constpool));
                        annotationsAttribute.addAnnotation(generatedValueAnnotation);

                        fieldInfo.addAttribute(annotationsAttribute);
                    }

                    // create getter for this field if it doesn't exist
                    String fieldName = field.getName();
                    String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                    String getterDec = "()" + fieldInfo.getDescriptor();
                    try {
                        CtMethod method = classToTransform.getMethod(getterName, getterDec);
                        System.out.println("!!! getter = " + method);
                    } catch (NotFoundException e) {
                        CtMethod getter = CtNewMethod.make(Modifier.PUBLIC, field.getType(), getterName, null, null, "return " + fieldName + ";", classToTransform);
                        classToTransform.addMethod(getter);
                    }

                    // create setter for this field if it doesn't exist
                    String setterName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                    String setterDec = "(" + fieldInfo.getDescriptor() + ")";
                    try {
                        CtMethod method = classToTransform.getMethod(setterName, setterDec);
                        System.out.println("!!! setter = " + method);
                    } catch (NotFoundException e) {
                        CtMethod setter = new CtMethod(CtClass.voidType, setterName, new CtClass[] { field.getType() }, classToTransform);
                        setter.setBody("{ " + fieldName + " = $1; }");
                        classToTransform.addMethod(setter);
                    }
                }

                // check for repository
                String repositoryClassName = "com.github.tbeerbower.repositories." + entityName + "Repository";
                System.out.println("Checking for " + repositoryClassName);
                CtClass repository = pool.getOrNull(repositoryClassName);
                // create a repository for the entity
                if (repository == null) {
                    System.out.println("Making interface " + repositoryClassName);

                    String jpaRepositoryClassname = "org.springframework.data.jpa.repository.JpaRepository";
                    CtClass jpaRepository = pool.get(jpaRepositoryClassname);

                    repository = pool.makeInterface(repositoryClassName, jpaRepository);

                    SignatureAttribute.ClassSignature cs = new SignatureAttribute.ClassSignature(
                            null,
                            null,
                            // Set interface and its generic params
                            new SignatureAttribute.ClassType[]{
                                    new SignatureAttribute.ClassType(jpaRepositoryClassname,
                                            new SignatureAttribute.TypeArgument[]{
                                                    new SignatureAttribute.TypeArgument(new SignatureAttribute.ClassType(classToTransform.getName())),
                                                    new SignatureAttribute.TypeArgument(new SignatureAttribute.ClassType(idClassName))
                                            }
                                    )
                            }
                    );
                    repository.setGenericSignature(cs.encode());
                    System.out.println("!!! Writing interface " + repositoryClassName);
                    repository.writeFile(classesDir);
                }

                // check for controller
                String controllerSimpleName = entityName + "Controller";
                String controllerClassName = "com.github.tbeerbower.controllers." + controllerSimpleName;
                System.out.println("Checking for " + controllerClassName);
                CtClass controller = pool.getOrNull(controllerClassName);
                if (controller == null) {
                    // create a controller for the entity

                    String baseControllerClassname = BaseController.class.getName();
                    CtClass baseController = pool.get(baseControllerClassname);

                    controller = pool.makeClass(controllerClassName, baseController);

                    SignatureAttribute.ClassSignature cs = new SignatureAttribute.ClassSignature(
                            null,
                            // Set interface and its generic params

                            new SignatureAttribute.ClassType(baseControllerClassname,
                                    new SignatureAttribute.TypeArgument[]{
                                            new SignatureAttribute.TypeArgument(new SignatureAttribute.ClassType(classToTransform.getName())),
                                            new SignatureAttribute.TypeArgument(new SignatureAttribute.ClassType(idClassName)),
                                            new SignatureAttribute.TypeArgument(new SignatureAttribute.ClassType(repository.getName()))
                                    }
                            ),
                            null
                    );
                    controller.setGenericSignature(cs.encode());

                    ClassFile classFile = controller.getClassFile();
                    ConstPool constpool = classFile.getConstPool();

                    AnnotationsAttribute annotationsAttribute = new AnnotationsAttribute(constpool, AnnotationsAttribute.visibleTag);
                    Annotation restControllerAnnotation = new Annotation(RestController.class.getName(), constpool);
                    Annotation requestMappingAnnotation = new Annotation(RequestMapping.class.getName(), constpool);
                    ArrayMemberValue amv = new ArrayMemberValue(constpool);
                    amv.setValue(new MemberValue[]{new StringMemberValue("/api/" + entityName.toLowerCase() + "s", constpool)});
                    requestMappingAnnotation.addMemberValue("value", amv);

                    annotationsAttribute.setAnnotations(new Annotation[]{restControllerAnnotation, requestMappingAnnotation});

                    classFile.addAttribute(annotationsAttribute);

                    CtConstructor constructor = CtNewConstructor.make("public " + controllerSimpleName + "(" + repositoryClassName + " repository) {super(repository);}", controller);
                    controller.addConstructor(constructor);

                    controller.writeFile(classesDir);
                }
            }
        } catch (NotFoundException | CannotCompileException | IOException e) {
            throw new JavassistBuildException(e);
        }
    }

    @Override
    public void configure(final Properties properties) {
        if (null == properties) {
            return;
        }
        this.targetDir = properties.getProperty(TARGET_DIR_PROPERTY_KEY);
    }
}