'use strict';

import _ from 'lodash';

export default class AuthService {
  constructor($http, $log, $q) {
    'ngInject';

    this.$log = $log;
    this.$q = $q;
    this.$http = $http;

    this.currentUser = null;
  }

  login(username, password) {
    let defer = this.$q.defer();

    this.$http
      .post('./api/login', {
        user: username,
        password: password
      })
      .then(response => defer.resolve(response.data))
      .catch(err => defer.reject(err));

    return defer.promise;
  }

  logout() {
    return this.$http.get('./api/logout').then(
      /*data*/ () => {
        this.currentUser = null;
      }
    );
  }

  current() {
    return this.$http
      .get('./api/user/current')
      .then(response => {
        this.currentUser = response.data;

        return this.$q.resolve(this.currentUser);
      })
      .catch(err => {
        this.currentUser = null;

        return this.$q.reject(err);
      });
  }

  isOrgAdmin(user) {
    let re = /orgadmin/i;

    return re.test(user.roles);
  }

  isSuperAdmin(user) {
    let re = /superadmin/i;

    return re.test(user.roles);
  }

  hasRole(roles) {
    if (!this.currentUser) {
      return false;
    }

    return !_.isEmpty(_.intersection(this.currentUser.roles, roles));
  }

  ssoLogin(code, success, failure) {
    var url = angular.isDefined(code) ? './api/ssoLogin?code=' + code : './api/ssoLogin';
    console.log(url)
    this.$http.post(url,
        { }).then(function (response) {
            success(response.data, response.status, response.headers, response.config);
            console.log('response success', response);
        }).catch(function (error) {
            console.log('error', error);
        });

    };
  }
