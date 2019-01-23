'use strict';

import LoginController from './login.controller';
import tpl from './login.page.html';

const loginPageModule = angular
  .module('login-module', ['ui.router'])
  .config($stateProvider => {
    'ngInject';

    $stateProvider.state('login', {
      url: '/login',
      component: 'loginPage'
    });
  })
  .component('loginPage', {
    controller: LoginController,
    templateUrl: tpl,
    resolve: {
        appConfig: function(VersionSrv) {
                     return VersionSrv.get();
                  }
    },
    params: {
        autoLogin: false
    },
  });

export default loginPageModule;
