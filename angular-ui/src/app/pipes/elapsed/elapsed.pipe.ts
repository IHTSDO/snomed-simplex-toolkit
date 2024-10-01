import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'elapsed',
})
export class ElapsedPipe implements PipeTransform {

  transform(milliseconds: number): string {
    // Convert milliseconds to hours, minutes, and seconds
    const seconds = Math.floor((milliseconds / 1000) % 60);
    const minutes = Math.floor((milliseconds / (1000 * 60)) % 60);
    const hours = Math.floor((milliseconds / (1000 * 60 * 60)) % 24);

    // Format the duration as HH:mm:ss
    const hoursStr = hours.toString().padStart(2, '0');
    const minutesStr = minutes.toString().padStart(2, '0');
    const secondsStr = seconds.toString().padStart(2, '0');

    return `${hoursStr}:${minutesStr}:${secondsStr}`;
  }

}
