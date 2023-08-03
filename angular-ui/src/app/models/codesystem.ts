export class NewCodesystem {
    name: string;
    owner: string;
    maintainerType: string;
    defaultLanguageReferenceSets: string[];
    countryCode: string;
    shortName: string;
    branchPath: string;
    dependantVersionEffectiveTime: number;
    defaultLanguageCode: string;

    constructor(name, owner, maintainerType, defaultLanguageReferenceSets, countryCode, shortName, branchPath, dependantVersionEffectiveTime, defaultLanguageCode) {
        this.name = name;
        this.owner = owner;
        this.maintainerType = maintainerType;
        this.defaultLanguageReferenceSets = defaultLanguageReferenceSets;
        this.countryCode = countryCode;
        this.shortName = shortName;
        this.branchPath = branchPath;
        this.dependantVersionEffectiveTime = dependantVersionEffectiveTime;
        this.defaultLanguageCode = defaultLanguageCode;
    }
}
