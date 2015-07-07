class CreateUsers < ActiveRecord::Migration
  def up
    execute %q{create extension if not exists "uuid-ossp";}
    create_table :users, id: :uuid do |t|
      t.uuid     :parent_id
      t.string   :email,      null: false
      t.string   :token,      null: false
      t.integer  :role,       null: false, default: 0
      t.datetime :created_at, null: false
    end
    add_index :users, :email, unique: true
    add_foreign_key :users, :users, name: "users_parent_id_fk", column: :parent_id, on_delete: :restrict
  end

  def down
    drop_table :users
  end
end
