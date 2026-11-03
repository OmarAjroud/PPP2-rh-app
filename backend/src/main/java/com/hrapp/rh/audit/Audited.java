package com.hrapp.rh.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marque une entité dont les créations, modifications et suppressions sont journalisées. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Audited {

    /** Propriétés à ne jamais enregistrer (par exemple un mot de passe). */
    String[] ignore() default {};
}