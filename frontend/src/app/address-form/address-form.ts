import { Component, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AddressInput } from '../models';

/**
 * The address, in the parts a geocoder can use.
 *
 * <p>Shared by the order editor and the depot form because both feed the same structured search,
 * and because the field set is the point: an apartment number typed into the street line is one of
 * the reasons real addresses fail to resolve, so it gets its own box.
 */
@Component({
  selector: 'app-address-form',
  imports: [FormsModule],
  templateUrl: './address-form.html',
  styleUrl: './address-form.scss',
})
export class AddressForm {
  /** Two-way, so a parent can seed it from a parsed order and read back what was edited. */
  readonly address = model.required<AddressInput>();

  patch(changes: Partial<AddressInput>): void {
    this.address.update((current) => ({ ...current, ...changes }));
  }
}
