export default class LoginController {
  constructor(
    $log,
    $state,
    $scope,
    $location,
    $uibModalStack,
    AuthService,
    NotificationService,
    // config,
    Roles
  ) {
    'ngInject';
    // this.config = this.main.config;
    this.$log = $log;
    this.$state = $state;
    this.$scope = $scope;
    this.$location = $location;
    this.$uibModalStack = $uibModalStack;
    this.AuthService = AuthService;
    this.NotificationService = NotificationService;
    this.Roles = Roles;
    this.params = {
      bar: 'foo'
    };
  }

  login() {
    this.params.username = angular.lowercase(this.params.username);

    this.AuthService.login(this.params.username, this.params.password)
      .then(() => {
        this.$state.go('index');
      })
      .catch(err => {
        if (err.status === 520) {
          this.NotificationService.handleError(
            'LoginController',
            err.data,
            err.status
          );
        } else {
          this.NotificationService.log(err.data.message, 'error');
        }
      });

  }

  ssoLogin(code) {
      this.AuthService.ssoLogin(code, function(data, status, headers) {
          var redirectLocation = headers().location;
          if(angular.isDefined(redirectLocation)) {
              window.location = redirectLocation;
          } else {
              this.$state.go('app.cases');
          }
      }, function(data, status) {
          if (status === 520) {
              this.NotificationService.error('LoginController', data, status);
          } else {
              this.NotificationService.log(data.message, 'error');
          }
          this.$location.url(this.$location.path());
      });
  }

  ssoEnabled() {
    // console.log(this.config)
      return true;
      // return this.config.authType.indexOf("oauth2") !== -1;
  }

  $onInit() {
    this.$uibModalStack.dismissAll();
  }
}
