/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import axios from 'axios';
import { ServerApi } from "src/services/ServerApi";
import { ref } from "vue";

/**
 * Performs a user authentication with HTTP Basic Auth strategy.
 */
export class AuthService {
  static isAuthenticated = ref(!!AuthService.login());

  /**
   * Tries to authenticate a user with the given `login` and `password` and returns `true` if the
   * attempt is successfully or `false` otherwise.
   */
  static tryAuthenticate(login: string, password: string): Promise<boolean> {
    return new Promise((resolve, reject) => {
      axios.head(`${ServerApi.ShardInfo}`, AuthService.options(login, password))
        .then((response) => {
          if (response.status === 200) {
            localStorage.login = login;
            localStorage.password = password;
            this.isAuthenticated.value = true;
            resolve(true);
          } else {
            reject(response);
          }
        })
        .catch((error) => {
          if (error.code === 'ERR_BAD_REQUEST' && error.response.status === 401) {
            resolve(false);
          } else {
            reject(error);
          }
        });
    });
  }

  /**
   * Removes login and password form the local storage.
   */
  static logout() {
    localStorage.removeItem("login");
    localStorage.removeItem("password");
    this.isAuthenticated.value = false;
  }

  /**
   * Creates auth options for the `axios` request with the given `login` and `password`.
   * @private
   */
  private static options(login: string, password: string):
    { auth: { username: string, password: string } } {
    return {
      auth: {
        username: login,
        password,
      },
    };
  }

  /**
   * Returns user's login from the local storage.
   * @private
   */
  private static login(): string {
    return localStorage.login;
  }

  /**
   * Returns user's password form the local storage.
   * @private
   */
  private static password(): string {
    return localStorage.password;
  }

  /**
   * Creates auth options for the `axios` request with login and password retrieved
   * from the local storage.
   */
  static authOptions():
    { auth: { username: string, password: string } } {
    return this.options(AuthService.login(), AuthService.password());
  }
}
