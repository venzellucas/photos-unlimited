package app.photos.unlimited;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Reusable native components and semantic theme roles. */
final class Ui {
  final Context context;

  Ui(Context context) {
    this.context = context;
  }

  int dp(int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  int color(int attr) {
    return MaterialColors.getColor(context, attr, "Photos Unlimited theme");
  }

  int surface() {
    return color(com.google.android.material.R.attr.colorSurface);
  }

  int ink() {
    return color(com.google.android.material.R.attr.colorOnSurface);
  }

  int muted() {
    return color(com.google.android.material.R.attr.colorOnSurfaceVariant);
  }

  int primary() {
    return color(androidx.appcompat.R.attr.colorPrimary);
  }

  LinearLayout column() {
    LinearLayout v = new LinearLayout(context);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  TextView text(String value, int size, boolean emphasis) {
    TextView v = new TextView(context);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(ink());
    v.setTypeface(null, emphasis ? Typeface.BOLD : Typeface.NORMAL);
    return v;
  }

  TextView supporting(String value) {
    TextView v = text(value, 14, false);
    v.setTextColor(muted());
    return v;
  }

  void space(View v, int top, int bottom) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(top), 0, dp(bottom));
    v.setLayoutParams(p);
  }

  LinearLayout card(LinearLayout parent, boolean prominent) {
    MaterialCardView card = new MaterialCardView(context);
    card.setRadius(dp(prominent ? 28 : 24));
    card.setCardElevation(0);
    card.setStrokeWidth(0);
    card.setCardBackgroundColor(
        color(
            prominent
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceContainer));
    LinearLayout body = column();
    body.setPadding(dp(20), dp(20), dp(20), dp(20));
    card.addView(body);
    space(card, 0, 16);
    parent.addView(card);
    return body;
  }

  MaterialButton button(String label, int icon, boolean filled) {
    MaterialButton v =
        new MaterialButton(
            context,
            null,
            filled
                ? com.google.android.material.R.attr.materialButtonStyle
                : androidx.appcompat.R.attr.borderlessButtonStyle);
    v.setText(label);
    v.setAllCaps(false);
    v.setMinHeight(dp(48));
    v.setMaxLines(4);
    v.setSingleLine(false);
    if (icon != 0) {
      v.setIconResource(icon);
      v.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
    }
    return v;
  }

  MaterialButton row(LinearLayout parent, String label, int icon, Runnable action) {
    MaterialButton v = button(label, icon, false);
    v.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    v.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    v.setMinHeight(dp(64));
    v.setPadding(dp(12), dp(10), dp(12), dp(10));
    space(v, 0, 4);
    parent.addView(v);
    v.setOnClickListener(x -> action.run());
    return v;
  }

  MaterialSwitch toggle(
      LinearLayout parent,
      String label,
      boolean checked,
      android.widget.CompoundButton.OnCheckedChangeListener listener) {
    MaterialSwitch v = new MaterialSwitch(context);
    v.setText(label);
    v.setTextSize(16);
    v.setTextColor(ink());
    v.setMinHeight(dp(64));
    v.setPadding(0, dp(8), 0, dp(8));
    v.setChecked(checked);
    v.setOnCheckedChangeListener(listener);
    parent.addView(v);
    return v;
  }
}
