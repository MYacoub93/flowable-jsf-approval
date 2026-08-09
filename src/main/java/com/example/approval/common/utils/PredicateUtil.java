/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.common.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.beanutils.BeanComparator;
import org.apache.commons.beanutils.BeanPredicate;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.functors.EqualPredicate;


public class PredicateUtil<T> {

    private final static Logger logger = Logger.getLogger(PredicateUtil.class.getName());

    public List<T> sortList(List<T> list, String propertyName) {

        try {
            Comparator<T> comparator = new BeanComparator(propertyName);
            Collections.sort(list, comparator);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }

        return list;
    }
    
    public List<T> sortListDdescending(List<T> list, String propertyName) {

        try {
            Comparator<T> comparator = new BeanComparator(propertyName);
            Collections.sort(list, comparator.reversed());
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }

        return list;
    }
    
    public Collection<T> selectObjects(List<T> list, String propertyName, String value) {

        Collection<T> filteredCollection = null;
        try {
            EqualPredicate nameEqlPredicate = new EqualPredicate(value);
            BeanPredicate beanPredicate = new BeanPredicate(propertyName, nameEqlPredicate);
            filteredCollection = CollectionUtils.select(list, beanPredicate);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }

        return filteredCollection;
    }

    public T selectObjectFromCollection(List<T> list, String propertyName, String value) {

        T object = null;
        try {
            EqualPredicate nameEqlPredicate = new EqualPredicate(value);
            BeanPredicate beanPredicate = new BeanPredicate(propertyName, nameEqlPredicate);

            object = (T) CollectionUtils.find(list, beanPredicate);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }

        return object;
    }

    public boolean checkExistInCollection(List<T> list, String propertyName, String value) {

        boolean exists = false;
        try {
            EqualPredicate nameEqlPredicate = new EqualPredicate(value);
            BeanPredicate beanPredicate = new BeanPredicate(propertyName, nameEqlPredicate);
            exists = CollectionUtils.exists(list, beanPredicate);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }

        return exists;

    }

    public <T> List getDuplicate(Collection<T> list) {

        final List<T> duplicatedObjects = new ArrayList<T>();
        Set<T> set = new HashSet<T>() {
            @Override
            public boolean add(T e) {
                if (contains(e)) {
                    duplicatedObjects.add(e);
                }
                return super.add(e);
            }
        };
        for (T t : list) {
            set.add(t);
        }
        return duplicatedObjects;
    }

    public <T> boolean hasDuplicate(Collection<T> list) {
        if (getDuplicate(list).isEmpty()) {
            return false;
        }
        return true;
    }

}
