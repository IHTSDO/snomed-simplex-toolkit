export class User {
    constructor(
        public active: boolean,
        public email: string,
        public firstName: string,
        public lastName: string,
        public login: string,
        public displayName: string,
        public roles: string[],
        public username: string,
        public clientAccess: string[]
    ) {}
}

export class Login {
    constructor(
        public login: string,
        public password: string,
        public rememberMe: boolean
    ) {}
}
