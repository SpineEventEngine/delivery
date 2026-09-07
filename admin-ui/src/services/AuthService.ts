/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import axios from 'axios';
import { ref } from 'vue';
import { useEndpoints } from 'src/services/endpoints';

/**
 * Performs a user authentication with an HTTP Basic Auth strategy.
 *
 * User credentials are stored in local storage openly and not encrypted. Stored credentials
 * never expire; it means that the user stays authenticated until the `logout()`
 * method will be called.
 */
export class AuthService {
  static isAuthenticated = ref(!!AuthService.username());

  static endpoints = useEndpoints();

  /**
   * Tries to authenticate a user with the given `login` and `password` and returns `true` if the
   * attempt is successfully or `false` otherwise.
   */
  static login(login: string, password: string): Promise<boolean> {
    return new Promise((resolve, reject) => {
      const options = {
        auth: {
          username: login,
          password,
        },
      };
      axios.head(`${this.endpoints.shardInfo}`, options)
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
   * Removes login and password from the local storage.
   */
  static logout() {
    localStorage.removeItem('login');
    localStorage.removeItem('password');
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
   * Returns the user's login or `null` if there is no authenticated user.
   * @private
   */
  static username(): string {
    return localStorage.login;
  }

  /**
   * Returns the user's password or `null` if there is no authenticated user.
   * @private
   */
  static password(): string {
    return localStorage.password;
  }
}
