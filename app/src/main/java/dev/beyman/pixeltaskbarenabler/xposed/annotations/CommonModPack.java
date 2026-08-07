package dev.beyman.pixeltaskbarenabler.xposed.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import dev.beyman.pixeltaskbarenabler.annotations.BaseModPack;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@BaseModPack( targetPackage = "")
public @interface CommonModPack { }
