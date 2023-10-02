import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'timeAgo'
})
export class TimeAgoPipe implements PipeTransform {

  transform(value: any, ...args: any[]): any {
    if (value) {
      const seconds = Math.floor((+new Date() - +new Date(value)) / 1000);
      if (seconds < 60) {
        return 'Just now';
      }
      const intervals = {
        'year': 31536000,
        'month': 2592000,
        'week': 604800,
        'day': 86400,
        'hour': 3600,
        'minute': 60
      };

      let counter;
      for (const unitName in intervals) {
        if (intervals.hasOwnProperty(unitName)) {
          counter = Math.floor(seconds / intervals[unitName]);
          if (counter > 0) {
            return `${counter} ${unitName}${counter > 1 ? 's' : ''} ago`;
          }
        }
      }
    }
    return value;
  }

}
