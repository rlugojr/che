/*
 * Copyright (c) 2015-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 */
'use strict';

/**
 * Defines a directive for displaying loader during project/workspace creating
 * @author Oleksii Kurinnyi
 */
export class CreateProjectLoader {
  /**
   * Default constructor that is using resource
   * @ngInject for Dependency injection
   */
  constructor ($q) {
    this.$q = $q;
    this.restrict = 'E';
    this.templateUrl = 'app/projects/create-project/loader/create-project-loader.html';

    // scope values
    this.scope = {
      model: '=cheModel'
    };
  }

  link ($scope, element) {
    let craneEl = element.find('.create-project-loader'),
      cargoEl = element.find('#load'),
      workingLogEl = element.find('.working-log'),
      oldStep, newStep;
    // $scope.$watch(() => {return $scope.step;}, (newVal,oldVal) => {
    $scope.$watch(() => {return $scope.model.getCurrentProgressStep();}, (newVal,oldVal) => {

      oldStep = oldVal;
      newStep = newVal;

      if (oldVal===newVal) {
        let step = 1;
        if (newVal>0){
          step = newVal;
        }
        craneEl.addClass('step-1');
        cargoEl.addClass('layer-1');
        return;
      }
      let stages = element.find('.stage'),
        outputs = stages.parent();

      stages.removeClass('active');
      outputs.find('.stage-' + newVal).addClass('active');

      if (workingLogEl.hasClass('untouched')) {
        stages.removeClass('opened');
        outputs.find('.stage-' + newVal).addClass('opened');
      }
    });

    element.find('.anim.trolley-block').bind('animationiteration', () => {
      if (newStep && oldStep){
        craneEl.removeClass('step-'+oldStep).addClass('step-'+newStep);
        cargoEl.removeClass('layer-'+oldStep).addClass('layer-'+newStep);
      }
    });

    // manual switching by stages
    workingLogEl.bind('click', (event) => {
      let targetEl = angular.element(event.target);
      if (!targetEl.hasClass('stage-title')) {
        return false;
      }
      let selectedStageEl = targetEl.parent(),
        stages = workingLogEl.find('.stage');
      workingLogEl.removeClass('untouched');
      stages.removeClass('opened');
      selectedStageEl.addClass('opened');
    });
  }
}
