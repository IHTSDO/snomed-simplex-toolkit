import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'camelToTitle'
})
export class CamelToTitlePipe implements PipeTransform {

  transform(value: string): string {
    if (!value) {
      return '';
    }

    // Split the string at each capital letter and join with a space
    const result = value.replace(/([A-Z])/g, ' $1')
                        .trim()
                        .toLowerCase()
                        .replace(/^\w/, c => c.toUpperCase());
    return result;
  }

}
